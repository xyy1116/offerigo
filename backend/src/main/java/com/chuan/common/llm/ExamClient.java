package com.chuan.common.llm;

import com.chuan.common.exception.BusinessException;
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

    private static final String TECHSTACK_PROMPT = """
            你是一名资深招聘专家与岗位画像分析师。请根据给定的岗位信息，判断岗位类别并提取核心技术栈要求。
            严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。输出结构如下：
            {
              "jobTitle": "岗位名称",
              "category": "technical 或 non-technical",
              "techStacks": ["技术栈1", "技术栈2"]
            }
            要求：
            1. category 只能是以下两个值之一：
               - "technical"：该岗位属于计算机/软件/互联网技术类（如软件开发、测试、运维、算法、数据、前端、后端、移动端、嵌入式、网络安全等），要求编程或专业技术能力；
               - "non-technical"：该岗位属于非技术类（如销售、市场、行政、人力资源、财务、运营、客服、产品等），不要求编程能力。
            2. techStacks 只保留岗位真正要求掌握的技术/工具/框架（如 Java、Vue、MySQL、Spring、Redis 等），按重要程度从高到低排列；非技术岗位可输出空数组。
            3. 不需要写掌握程度，只要名称简写即可。
            """;

    private static final String GENERATE_PROMPT = """
            你是一名资深技术笔试命题专家。请根据岗位信息与核心技术栈，定向生成贴合岗位实际的笔试练习题。
            严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。输出结构如下：
            {
              "questions": [
                {
                  "type": "single|multiple|short_answer|coding",
                  "stem": "题干",
                  "options": ["A. 选项A", "B. 选项B"],
                  "answer": "标准答案",
                  "analysis": "详细解析",
                  "difficulty": "basic|middle|advanced",
                  "knowledgePoint": "考查知识点",
                  "testCases": ["用例描述"],
                  "codeTestCases": [
                    {"input": "标准输入文本", "expectedOutput": "期望标准输出", "explanation": "用例说明"}
                  ]
                }
              ]
            }
            要求：
            1. type 只能取 single(单选)、multiple(多选)、short_answer(简答)、coding(编程) 四种。
            2. single/multiple 必须提供 options 数组；answer 填选项字母（单选如 "A"，多选如 "A,C"）。
            3. short_answer 的 answer 填参考答案要点；coding 的 answer 填参考实现或核心思路，并填 testCases 数组描述至少 2 个测试用例（输入与期望输出）。
            4. coding 必须额外提供 codeTestCases，至少 2 个，input 为程序从标准输入读取的完整文本，expectedOutput 为程序写到标准输出的期望文本；题干中需明确输入输出格式。答案需能在 JavaScript 或 Java 中通过标准输入输出实现。
            5. analysis 针对每道题给出清晰解析。
            6. difficulty 按基础:中级:高级约 5:3:2 分配。
            7. 题目内容必须紧扣岗位技术栈，避免无关的通用题。
            8. 同一批题目之间不得重复或高度相似；编程题必须选择不同题目，不要改写同一道题。
            9. 所有 answer 与 analysis 均需简洁明了、通俗易懂，符合人性化表达习惯，避免堆砌晦涩专业术语：简答题用要点式列点，编程题只给出核心思路与关键代码片段，避免长篇赘述。
            """;

    private static final String GRADE_PROMPT = """
            你是一名资深技术笔试阅卷官。请对用户的主观题/编程题作答进行专业批改。
            严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。输出结构如下：
            {
              "score": 0-该题满分的数字,
              "correct": true|false,
              "analysis": "本题评析与正确答案要点",
              "errorReason": "指出用户答案的错误或不完整之处（若无则写'基本正确'）",
              "extension": "围绕该知识点给出的延伸学习建议"
            }
            要求：
            1. 判断 correct 的核心依据是「用户答案的核心内容与逻辑是否正确」，而非是否与标准答案逐字一致。允许非关键性表述差异（同义改写、语序调整、措辞更口语化或更简洁）。只要抓住关键得分点、核心结论与逻辑成立，即可判定 correct=true。
            2. 仅当存在实质性错误（核心要点缺失、方向性错误、事实错误、逻辑错误）时才判定 correct=false，不要因措辞差异或省略非关键细节而判错。
            3. 基于关键词命中、逻辑完整性、技术准确性综合评分，按正确程度给分；核心正确但表述有差异时，可给满分或接近满分。
            4. 编程题请对照题目要求与测试用例，静态评估代码的正确性、边界处理与复杂度，不真实执行代码。
            5. analysis、errorReason、extension 均需简洁明了、通俗易懂，符合人性化表达，避免过度技术化或晦涩术语。
            """;

    private static final String REAL_ANSWER_PROMPT = """
            你是一名资深技术面试官。下面是从公开大厂面经聚合库抓取的真实面试题（部分只有题干）。
            请为每道题补充难度、知识点、参考答案要点和解析。
            严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。输出结构如下：
            {
              "items": [
                {
                  "index": 1,
                  "difficulty": "basic|middle|advanced",
                  "knowledgePoint": "考查知识点",
                  "answer": "参考答案要点",
                  "analysis": "简要解析",
                  "codeTestCases": [
                    {"input": "标准输入文本", "expectedOutput": "期望标准输出", "explanation": "用例说明"}
                  ]
                }
              ]
            }
            要求：
            1. index 必须与题目前的序号一致，不要增删题目。
            2. 回答要准确，符合主流技术面试的标准答案；不确定的内容不要编造，可写“开放题，重点考察思路”。
            3. short_answer 用要点式回答；coding 给出核心思路、关键步骤与复杂度，必要时给关键代码片段。
            4. coding 题目必须额外提供 codeTestCases（至少 2 个），input 为程序从标准输入读取的完整文本，expectedOutput 为期望标准输出；需自行定义清晰的输入输出格式，答案应能通过 JavaScript 或 Java 的标准输入输出实现。
            5. analysis 简洁说明考点与易错点，避免长篇大论。
            6. 难度按题目真实难度判断，不要全部给 middle。
            """;

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
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}