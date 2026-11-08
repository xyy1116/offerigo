package com.chuan.module.interview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分岗位面试题库服务：加载 question_bank.json，按方向返回参考高频题，
 * 并根据岗位名称 / 技术栈推断题库方向（java / frontend / test / general），
 * 用于在面试前注入校准提问的方向与深度，避免入门题与偏门题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionBankService {

    private static final String QUESTION_BANK_PATH = "interview/question_bank.json";

    private final ObjectMapper objectMapper;

    /** 方向 -> 题目列表（保持 JSON 顺序） */
    private final Map<String, List<String>> bank = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource(QUESTION_BANK_PATH);
            try (InputStream in = resource.getInputStream();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonNode root = objectMapper.readTree(reader);
                root.fieldNames().forEachRemaining(key -> {
                    List<String> questions = new ArrayList<>();
                    root.get(key).forEach(q -> questions.add(q.asText()));
                    bank.put(key, questions);
                });
            }
            log.info("面试题库加载完成, 方向数={}", bank.size());
        } catch (Exception e) {
            log.error("面试题库加载失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据岗位名称 + 技术栈推断题库方向。
     * 命中「Java / 后端 / 相关语言」返回 java；「前端」返回 frontend；「测试」返回 test；否则 general。
     */
    public String inferKey(String jobName, String techStack) {
        String text = (jobName == null ? "" : jobName) + " " + (techStack == null ? "" : techStack);
        String lower = text.toLowerCase();
        if (containsAny(lower, "java", "后端", "spring", "mysql", "redis", "jvm", "golang", "go开发",
                "c++", "python", "算法", "大数据", "运维")) {
            return "java";
        }
        if (containsAny(lower, "前端", "vue", "react", "web", "javascript", "typescript", "h5", "小程序", "uni-app", "node")) {
            return "frontend";
        }
        if (containsAny(lower, "测试", "test", "qa", "质量", "自动化")) {
            return "test";
        }
        return "general";
    }

    /** 按方向返回参考题库；未知方向兜底 general。 */
    public List<String> getQuestions(String key) {
        List<String> questions = bank.get(key);
        if (questions == null || questions.isEmpty()) {
            questions = bank.get("general");
        }
        return questions == null ? new ArrayList<>() : questions;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}