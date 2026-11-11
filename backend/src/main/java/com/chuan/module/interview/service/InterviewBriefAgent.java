package com.chuan.module.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.llm.QwenChatClient;
import com.chuan.module.interview.ws.InterviewSession;
import com.chuan.module.jobmatch.entity.KnowledgeEntry;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 面试官调研 Agent：开场前让模型自己决定要查什么资料（候选人素材库 / 面试题库），
 * 再据此列出本场最值得深挖的考察点，作为面试官系统提示的一部分。
 *
 * <p>只跑一次（开场），失败或未命中直接返回空串，面试照旧进行——工具是加分项，不能成为故障点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewBriefAgent {

    private static final int MAX_ROUNDS = 3;
    private static final int MAX_MATERIALS = 5;
    private static final int MAX_QUESTIONS = 6;

    private final QwenChatClient qwenChatClient;
    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    @Value("${ai.tools.enabled:true}")
    private boolean enabled;

    /** 简报正文 + 本次实际发生的工具调用，用于前端展示 Agent 的检索过程。 */
    public record Brief(String text, List<Map<String, Object>> steps) {
        public static Brief empty() {
            return new Brief("", List.of());
        }
    }

    /** 生成面试官简报；关闭、无 Key、模型或工具出错时返回空简报。 */
    public Brief buildBrief(InterviewSession session) {
        if (!enabled || session == null) {
            return Brief.empty();
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        try {
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(message("system",
                    "你是面试官的调研助手。你可以调用工具查询候选人自己的简历素材库和技术面试题库。\n"
                            + "要求：\n"
                            + "1. 必须先调用工具取回事实，再给结论，不允许只凭岗位名称推测候选人经历；\n"
                            + "2. 最多调用 3 次工具；\n"
                            + "3. 最终只输出 3-5 条「值得深挖的考察点」，每条一行、以「- 」开头、不超过 40 字，不要输出过程说明。"));
            messages.add(message("user",
                    "本场面试岗位：" + (session.getPosition() == null ? "" : session.getPosition())
                            + "\n技术栈：" + (session.getTechStack() == null ? "" : session.getTechStack())
                            + "\n请检索该候选人的素材库与相关题库，给出考察点。"));
            BiFunction<String, String, String> executor = (name, args) -> {
                String result = executeTool(name, args, session.getUserId());
                steps.add(trace(name, args, result));
                return result;
            };
            String brief = qwenChatClient.chatWithTools(messages, tools(), executor, MAX_ROUNDS).trim();
            return new Brief(StringUtils.hasText(brief) ? brief : "", steps);
        } catch (Exception e) {
            log.warn("生成面试官简报失败，按无简报继续: {}", e.getMessage());
            return new Brief("", steps);
        }
    }

    /** 记录一次工具调用，供前端展示「查了什么、查到几条」。 */
    private Map<String, Object> trace(String name, String arguments, String result) {
        String keyword = "";
        try {
            keyword = objectMapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments)
                    .path("keyword").asText("");
        } catch (Exception ignored) {
            // 参数不是 JSON 时只记工具名
        }
        int hits = 0;
        try {
            JsonNode node = objectMapper.readTree(result);
            if (node.isArray()) {
                hits = node.size();
            }
        } catch (Exception ignored) {
            // 结果不是 JSON 数组时按 0 条计
        }
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("tool", name);
        step.put("keyword", keyword);
        step.put("hits", hits);
        return step;
    }

    private String executeTool(String name, String arguments, Long userId) {
        JsonNode root;
        try {
            root = objectMapper.readTree(arguments == null || arguments.isBlank() ? "{}" : arguments);
        } catch (Exception e) {
            return "{\"error\":\"参数不是合法 JSON\"}";
        }
        String keyword = root.path("keyword").asText("").trim();
        if (!StringUtils.hasText(keyword)) {
            return "{\"error\":\"keyword 不能为空\"}";
        }
        try {
            if ("search_resume_materials".equals(name)) {
                return objectMapper.writeValueAsString(searchMaterials(keyword, userId));
            }
            if ("search_question_bank".equals(name)) {
                return objectMapper.writeValueAsString(questionBankService.search(keyword, MAX_QUESTIONS));
            }
        } catch (Exception e) {
            log.warn("工具 {} 执行失败: {}", name, e.getMessage());
        }
        return "{\"error\":\"未知工具或执行失败\"}";
    }

    private List<Map<String, String>> searchMaterials(String keyword, Long userId) {
        LambdaQueryWrapper<KnowledgeEntry> query = new LambdaQueryWrapper<KnowledgeEntry>()
                .eq(KnowledgeEntry::getUserId, userId)
                .like(KnowledgeEntry::getContent, keyword)
                .last("limit " + MAX_MATERIALS);
        List<Map<String, String>> result = new ArrayList<>();
        for (KnowledgeEntry entry : knowledgeEntryMapper.selectList(query)) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", entry.getType());
            item.put("content", entry.getContent());
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private List<Map<String, Object>> tools() {
        Map<String, Object> materials = tool("search_resume_materials",
                "按关键词检索候选人「我的简历素材」库，返回匹配素材的类型与内容；用于确认项目细节、技术栈与成果。",
                "检索关键词，如项目名、技术栈、业务领域");
        Map<String, Object> bank = tool("search_question_bank",
                "按关键词检索技术面试题库，返回匹配的题目原文；用于确定该岗位该技术方向的考察深度。",
                "检索关键词，如 Java、MySQL、索引、并发");
        return List.of(materials, bank);
    }

    private Map<String, Object> tool(String name, String description, String keywordDesc) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", Map.of("keyword", Map.of(
                "type", "string",
                "description", keywordDesc)));
        parameters.put("required", List.of("keyword"));
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        return Map.of("type", "function", "function", function);
    }
}