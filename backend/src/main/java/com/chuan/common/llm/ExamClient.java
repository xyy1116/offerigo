package com.chuan.common.llm;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.util.LlmText;
import com.chuan.common.util.PromptLoader;
import com.chuan.module.exam.vo.CodeTestCaseVO;
import com.chuan.module.exam.vo.GradingItemVO;
import com.chuan.module.exam.vo.QuestionVO;
import com.chuan.module.exam.vo.TechStackVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔试助手 LLM 客户端：负责岗位技术栈提取、定向出题与主观题智能批改。
 * 复用 DeepSeek 文本模型，使用 JSON 结构化输出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamClient {

    private static final String TECHSTACK_PROMPT = PromptLoader.get("exam-techstack");

    private static final String GENERATE_PROMPT = PromptLoader.get("exam-generate");

    private static final String GRADE_PROMPT = PromptLoader.get("exam-grade");

    private static final String REAL_ANSWER_PROMPT = PromptLoader.get("exam-real-answer");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.deepseek.base-url}")
    private String baseUrl;

    @Value("${llm.deepseek.api-key}")
    private String apiKey;

    @Value("${llm.deepseek.model}")
    private String model;

    /** 提取岗位核心技术栈。 */
    public TechStackVO extractTechStack(String jdText, String jobTitle, List<String> dimensions,
                                        List<String> missingRequirements) {
        String userPrompt = buildJdPrompt(jdText, jobTitle, dimensions, missingRequirements);
        JsonNode content = doJsonCall(TECHSTACK_PROMPT, userPrompt, 0.2);
        TechStackVO vo = new TechStackVO();
        vo.setJobTitle(content.path("jobTitle").asText(StringUtils.hasText(jobTitle) ? jobTitle : ""));
        List<String> stacks = new ArrayList<>();
        for (JsonNode node : content.path("techStacks")) {
            if (node.isTextual() && StringUtils.hasText(node.asText())) {
                stacks.add(node.asText());
            }
        }
        vo.setTechStacks(stacks);
        String category = content.path("category").asText("");
        if (!"technical".equals(category) && !"non-technical".equals(category)) {
            category = stacks.isEmpty() ? "non-technical" : "technical";
        }
        vo.setCategory(category);
        return vo;
    }

    /** 定向生成笔试题，按批累加以避免单次输出过长。 */
    public List<QuestionVO> generateQuestions(String jdText, String jobTitle, TechStackVO techStack,
                                              int count, String difficulty, String mode, String knowledgePoint,
                                              String questionPreference, List<String> avoidStems) {
        List<QuestionVO> result = new ArrayList<>();
        int batchSize = Math.min(4, Math.max(1, count));
        int remaining = count;
        int batchIndex = 0;
        while (remaining > 0) {
            int current = Math.min(batchSize, remaining);
            // 只有第一批强制包含编程题，后续批次恢复自动混合，避免整套卷子全是编程题
            String batchPreference = "coding".equals(questionPreference) && batchIndex > 0
                    ? "auto" : questionPreference;
            List<QuestionVO> batch = generateBatch(jdText, jobTitle, techStack, count, batchIndex + 1,
                    current, difficulty, mode, knowledgePoint, batchPreference, avoidStems);
            result.addAll(batch);
            remaining -= current;
            batchIndex++;
            if (batch.isEmpty()) {
                break;
            }
        }
        return result;
    }

    private List<QuestionVO> generateBatch(String jdText, String jobTitle, TechStackVO techStack,
                                           int totalCount, int batchNo, int current, String difficulty,
                                           String mode, String knowledgePoint, String questionPreference,
                                           List<String> avoidStems) {
        StringBuilder sb = new StringBuilder();
        sb.append("【岗位名称】").append(StringUtils.hasText(jobTitle) ? jobTitle : "（从JD推断）").append("\n");
        sb.append("【核心技术栈】").append(techStack == null ? "" : String.join("、", techStack.getTechStacks())).append("\n");
        sb.append("【岗位 JD】（供参考）\n").append(jdText).append("\n\n");
        sb.append("本次共需生成 ").append(totalCount).append(" 题，这是第 ").append(batchNo).append(" 批，本批生成 ").append(current).append(" 题。\n");
        if (avoidStems != null && !avoidStems.isEmpty()) {
            sb.append("【已有题目，请勿重复或改写】\n");
            for (String stem : avoidStems.stream().limit(12).toList()) {
                if (!StringUtils.hasText(stem)) {
                    continue;
                }
                sb.append("- ").append(stem.length() > 120 ? stem.substring(0, 120) : stem).append("\n");
            }
        }
        if (StringUtils.hasText(difficulty)) {
            sb.append("题目难度限定为：").append(difficulty).append("。\n");
        }
        if (StringUtils.hasText(mode) && StringUtils.hasText(knowledgePoint)) {
            sb.append("请围绕知识点「").append(knowledgePoint).append("」出题。\n");
        }
        boolean technical = techStack != null && "technical".equals(techStack.getCategory());
        if (technical) {
            if ("objective".equals(questionPreference)) {
                sb.append("题型只允许单选题(single)、多选题(multiple)、简答题(short_answer)，严禁出现编程题(coding)或代码相关内容。");
            } else if ("coding".equals(questionPreference)) {
                sb.append("本批题目必须至少包含 1 道编程题(coding)，其余题型可混合；编程题必须提供 codeTestCases。");
            } else {
                sb.append("题型请混合包含单选题、多选题、简答题、编程题，确保内容贴合上述岗位技术栈。");
            }
        } else {
            sb.append("该岗位为非技术岗，题型只允许包含单选题(single)、多选题(multiple)、简答题(short_answer)，严禁出现任何编程题或代码相关内容，请围绕岗位职责、行业知识与通用职业能力出题。");
        }

        JsonNode content = doJsonCall(GENERATE_PROMPT, sb.toString(), 0.4);
        List<QuestionVO> questions = new ArrayList<>();
        for (JsonNode node : content.path("questions")) {
            try {
                QuestionVO q = new QuestionVO();
                q.setType(node.path("type").asText("single"));
                q.setStem(node.path("stem").asText(""));
                List<String> options = new ArrayList<>();
                for (JsonNode opt : node.path("options")) {
                    if (opt.isTextual()) {
                        options.add(opt.asText());
                    }
                }
                q.setOptions(options);
                q.setAnswer(node.path("answer").asText(""));
                q.setAnalysis(node.path("analysis").asText(""));
                q.setDifficulty(node.path("difficulty").asText("middle"));
                q.setKnowledgePoint(node.path("knowledgePoint").asText(""));
                List<String> testCases = new ArrayList<>();
                for (JsonNode tc : node.path("testCases")) {
                    if (tc.isTextual()) {
                        testCases.add(tc.asText());
                    }
                }
                q.setTestCases(testCases);
                q.setCodeTestCases(parseCodeTestCases(node));
                if (StringUtils.hasText(q.getStem())) {
                    questions.add(q);
                }
            } catch (Exception e) {
                log.warn("解析单题失败，已跳过", e);
            }
        }
        return questions;
    }

    /** 为大厂真题批量补充参考答案、解析与难度。 */
    public void enrichRealQuestions(List<QuestionVO> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下 ").append(questions.size()).append(" 道真实面试题补充参考答案：\n");
        for (int i = 0; i < questions.size(); i++) {
            QuestionVO q = questions.get(i);
            sb.append(i + 1).append(". 【").append(q.getType()).append("】").append(q.getStem()).append("\n");
        }
        JsonNode content = doJsonCall(REAL_ANSWER_PROMPT, sb.toString(), 0.2);
        for (JsonNode node : content.path("items")) {
            int index = node.path("index").asInt(0) - 1;
            if (index < 0 || index >= questions.size()) {
                continue;
            }
            QuestionVO q = questions.get(index);
            if (!StringUtils.hasText(q.getAnswer())) {
                q.setAnswer(node.path("answer").asText(""));
            }
            if (!StringUtils.hasText(q.getAnalysis())) {
                q.setAnalysis(node.path("analysis").asText(""));
            }
            if (!StringUtils.hasText(q.getKnowledgePoint())) {
                q.setKnowledgePoint(node.path("knowledgePoint").asText(""));
            }
            String difficulty = node.path("difficulty").asText("");
            if (!StringUtils.hasText(q.getDifficulty()) || "middle".equals(q.getDifficulty())) {
                if ("basic".equals(difficulty) || "middle".equals(difficulty) || "advanced".equals(difficulty)) {
                    q.setDifficulty(difficulty);
                }
            }
            if ("coding".equals(q.getType())
                    && (q.getCodeTestCases() == null || q.getCodeTestCases().isEmpty())) {
                q.setCodeTestCases(parseCodeTestCases(node));
            }
        }
    }

    private List<CodeTestCaseVO> parseCodeTestCases(JsonNode node) {
        List<CodeTestCaseVO> out = new ArrayList<>();
        for (JsonNode tc : node.path("codeTestCases")) {
            String input = tc.path("input").asText("");
            String expected = tc.path("expectedOutput").asText("");
            if (!StringUtils.hasText(input) && !StringUtils.hasText(expected)) {
                continue;
            }
            CodeTestCaseVO testCase = new CodeTestCaseVO();
            testCase.setInput(input);
            testCase.setExpectedOutput(expected);
            testCase.setExplanation(tc.path("explanation").asText(""));
            out.add(testCase);
        }
        return out;
    }

    /** 主观题/编程题智能批改（不真实运行代码）。 */
    public GradingItemVO gradeSubjective(QuestionVO question, String userAnswer, BigDecimal fullScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题型】").append(question.getType()).append("\n");
        sb.append("【知识点】").append(question.getKnowledgePoint()).append("\n");
        sb.append("【题目】\n").append(question.getStem()).append("\n");
        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
            sb.append("【选项】\n").append(String.join("\n", question.getOptions())).append("\n");
        }
        sb.append("【标准答案/参考要点】\n").append(question.getAnswer()).append("\n");
        if (question.getTestCases() != null && !question.getTestCases().isEmpty()) {
            sb.append("【测试用例】\n").append(String.join("\n", question.getTestCases())).append("\n");
        }
        sb.append("【本题满分】").append(fullScore).append("分\n");
        sb.append("【用户作答】\n").append(StringUtils.hasText(userAnswer) ? userAnswer : "（未作答）");

        JsonNode content = doJsonCall(GRADE_PROMPT, sb.toString(), 0.3);
        GradingItemVO item = new GradingItemVO();
        item.setFullScore(fullScore);
        item.setScore(BigDecimal.valueOf(content.path("score").asDouble(0.0)).setScale(1, java.math.RoundingMode.HALF_UP));
        item.setCorrect(content.path("correct").asBoolean(false));
        item.setAnalysis(content.path("analysis").asText(""));
        item.setErrorReason(content.path("errorReason").asText(""));
        item.setExtension(content.path("extension").asText(""));
        item.setStandardAnswer(question.getAnswer());
        return item;
    }

    private String buildJdPrompt(String jdText, String jobTitle, List<String> dimensions, List<String> missingRequirements) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(jobTitle)) {
            sb.append("【岗位名称】").append(jobTitle).append("\n");
        }
        if (dimensions != null && !dimensions.isEmpty()) {
            sb.append("【能力维度】").append(String.join("、", dimensions)).append("\n");
        }
        if (missingRequirements != null && !missingRequirements.isEmpty()) {
            sb.append("【岗位要求要点】").append(String.join("；", missingRequirements)).append("\n");
        }
        sb.append("【岗位 JD 原文】\n").append(jdText);
        return sb.toString();
    }

    /** 统一的 JSON 结构化调用：返回解析后的 content JsonNode。 */
    private JsonNode doJsonCall(String systemPrompt, String userPrompt, double temperature) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return objectMapper.readTree(stripCodeFences(content));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("笔试助手 LLM 调用失败", e);
            throw new BusinessException("AI 出题/批改失败，请稍后重试");
        }
    }

    private String stripCodeFences(String content) {
        return LlmText.stripCodeFences(content);
    }
}