package com.chuan.common.llm;

import com.chuan.common.exception.BusinessException;
import com.chuan.module.match.vo.MatchResultVO;
import com.chuan.module.resume.dto.CareerAnalysisVO;
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

import java.util.List;
import java.util.Map;

/**
 * DeepSeek 文本模型客户端：负责简历与 JD 的语义匹配分析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private static final String SYSTEM_PROMPT = """
            你是一名资深 HR 与技术面试官，负责对候选人简历与岗位 JD 进行匹配分析。
            请严格按照 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。
            输出 JSON 结构如下：
            {
              "overallScore": 0-100 的数字(保留一位小数),
              "jobTitle": "从 JD 中提取的岗位名称",
              "summary": "总体匹配评价，2-3 句话",
              "dimensions": [
                {"name": "技能维度名称", "score": 0-100 的数字, "remark": "简短评述"}
              ],
              "matchedPoints": ["简历中与 JD 匹配的关键词/技能/经验，使用原文短语"],
              "missingRequirements": ["JD 中要求但简历缺失或不匹配的要求，使用原文短语"]
            }
            要求：
            1. dimensions 覆盖该岗位最核心的 5-7 个能力维度（如技术栈、工作经验、学历、项目经验、软技能等，按 JD 实际内容确定）。
            2. matchedPoints 与 missingRequirements 尽量使用简历和 JD 中的原文短语，便于前端精确定位高亮。
            """;

    private static final String POLISH_PROMPT =
            "你是一名资深的简历优化专家，负责对用户提供的简历内容进行润色优化。\n" +
            "请在保留原意和真实信息的前提下：优化语言表达、补充专业术语、突出亮点与成果、使描述更简洁专业。\n" +
            "严格按 JSON 格式输出，不要输出 JSON 以外的内容，不要使用 Markdown 代码块。输出结构如下：\n" +
            "{\"polished\": \"润色后的完整文本\"}\n" +
            "注意：不要编造不存在的经历、数据或技能；如果原文信息缺失，保持留白或通用表达。";

    private static final String TAILOR_PROMPT =
            "你是一名资深的简历招聘优化专家，负责将候选人简历与目标岗位 JD 进行对齐，生成「针对该岗位定制」的简历改写内容。\n"
            + "务必实事求是：只优化表达、突出与岗位相关的既有经历与技能，绝不编造候选人未具备的经历、项目、数据或技能。\n"
            + "严格按 JSON 格式输出，不要输出 JSON 以外的内容，不要使用 Markdown 代码块。输出结构如下：\n"
            + "{\n"
            + "  \"selfEvaluation\": \"针对岗位重写后的自我评价\",\n"
            + "  \"internships\": [{\"description\": \"针对岗位重写后的该段实习/项目描述\"}],\n"
            + "  \"campusActivities\": [{\"duty\": \"针对岗位重写后的该段校园经历职责\"}],\n"
            + "  \"highlights\": [\"定制要点1\", \"定制要点2\"]\n"
            + "}\n"
            + "要求：\n"
            + "1. internships 数组长度与原文实习/项目段数一致，按序对应；原文无实习则返回 []。\n"
            + "2. campusActivities 数组长度与原文校园经历段数一致，按序对应；原文无则返回 []。\n"
            + "3. highlights 为 3-5 条定制优化要点，说明本次改写的方向。\n"
            + "4. 描述需自然嵌入岗位 JD 中的关键技能/术语，但仅限候选人本身具备的。";

    private static final String COVER_LETTER_PROMPT =
            "你是一名资深求职指导专家，负责为候选人撰写一封针对具体岗位的中文求职信。\n"
            + "务必实事求是：基于候选人真实简历，突出与岗位要求匹配的关键优势，绝不编造经历、数据或技能。\n"
            + "严格按 JSON 格式输出，不要输出 JSON 以外的内容，不要使用 Markdown 代码块。输出结构如下：\n"
            + "{\n"
            + "  \"subject\": \"应聘 {岗位名称} 求职信\",\n"
            + "  \"greeting\": \"尊敬的招聘负责人：\",\n"
            + "  \"body\": \"求职信正文，分段，每段用换行分隔，约 200-350 字\"\n"
            + "}\n"
            + "要求：\n"
            + "1. body 应包含：开篇表明应聘岗位与来源、核心匹配优势（2-3 点，紧扣 JD）、表达加入意愿与感谢。\n"
            + "2. 语言专业、诚恳、精炼，避免空话套话。\n"
            + "3. 只使用与岗位要求直接相关的经历、技能和成果；与岗位无关的项目、课程、兴趣、证书不要罗列。";

    private static final String CAREER_PROMPT =
            "你是一名资深职业规划师与招聘专家，负责评估候选人简历与其目标岗位的匹配度，并给出可执行的发展建议。\n" +
            "严格按 JSON 格式输出，不要输出 JSON 以外的内容，不要使用 Markdown 代码块。输出结构如下：\n" +
            "{\n" +
            "  \"fitScore\": 0-100 的整数,\n" +
            "  \"overview\": \"总体评价，2-3 句话\",\n" +
            "  \"recommendations\": [\n" +
            "    {\"priority\": 1, \"category\": \"技能提升|经验积累|资质提升\", \"title\": \"简短标题\", \"detail\": \"具体可执行的建议\"}\n" +
            "  ]\n" +
            "}\n" +
            "要求：\n" +
            "1. recommendations 按优先级从高到低排列（priority 从 1 开始递增），共 3-6 条。\n" +
            "2. category 只能取 \"技能提升\"、\"经验积累\"、\"资质提升\" 三个值之一。\n" +
            "3. 建议要具体、可执行，紧扣候选人现状与目标岗位的真实要求差距，不要编造候选人未具备的经历。";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.deepseek.base-url}")
    private String baseUrl;

    @Value("${llm.deepseek.api-key}")
    private String apiKey;

    @Value("${llm.deepseek.model}")
    private String model;

    public MatchResultVO analyze(String resumeText, String jdText) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        String userPrompt = "请分析以下简历与岗位 JD 的匹配度。\n\n【简历】\n" + resumeText
                + "\n\n【岗位 JD】\n" + jdText;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return objectMapper.readValue(stripCodeFences(content), MatchResultVO.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用失败", e);
            throw new BusinessException("智能匹配分析失败，请稍后重试");
        }
    }

    public String polishText(String text, String section) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        String label = StringUtils.hasText(section) ? section : "简历内容";
        String userPrompt = "请润色以下简历【" + label + "】内容。\n\n" + text;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", POLISH_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            JsonNode polished = objectMapper.readTree(stripCodeFences(content));
            return polished.path("polished").asText("");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 润色失败", e);
            throw new BusinessException("AI 润色失败，请稍后重试");
        }
    }

    public CareerAnalysisVO analyzeCareer(String resumeSummary, String targetPosition) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }

        String userPrompt = "【目标岗位】\n" + targetPosition + "\n\n【候选人完整简历】\n" + resumeSummary;

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", CAREER_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return objectMapper.readValue(stripCodeFences(content), CareerAnalysisVO.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 职业分析失败", e);
            throw new BusinessException("职业发展分析失败，请稍后重试");
        }
    }

    /** 针对目标岗位定制简历：改写自我评价、实习/校园描述，返回 JSON 字符串。 */
    public String tailorResume(String resumeSummary, String jobName, String jobRequirement) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }
        String userPrompt = "【目标岗位】\n" + (jobName == null ? "" : jobName)
                + "\n\n【岗位要求】\n" + (jobRequirement == null ? "" : jobRequirement)
                + "\n\n【候选人简历】\n" + resumeSummary
                + "\n\n请只使用与岗位要求相关的简历内容，无关内容不要写入。";
        return chatJson(TAILOR_PROMPT, userPrompt, 0.3);
    }

    /** 针对目标岗位撰写中文求职信，返回 JSON 字符串。 */
    public String writeCoverLetter(String resumeSummary, String jobName, String companyName, String jobRequirement) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }
        String userPrompt = "【目标岗位】\n" + (jobName == null ? "" : jobName)
                + "\n【目标公司】\n" + (companyName == null ? "" : companyName)
                + "\n\n【岗位要求】\n" + (jobRequirement == null ? "" : jobRequirement)
                + "\n\n【候选人简历】\n" + resumeSummary
                + "\n\n请只使用与岗位要求相关的简历内容，无关内容不要写入。";
        return chatJson(COVER_LETTER_PROMPT, userPrompt, 0.4);
    }

    private String chatJson(String systemPrompt, String userPrompt, double temperature) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", temperature,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return stripCodeFences(root.path("choices").path(0).path("message").path("content").asText());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 调用失败", e);
            throw new BusinessException("AI 分析失败，请稍后重试");
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