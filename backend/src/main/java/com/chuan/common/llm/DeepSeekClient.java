package com.chuan.common.llm;

import com.chuan.common.ai.AiSafetyGuard;
import com.chuan.common.exception.BusinessException;
import com.chuan.common.util.LlmText;
import com.chuan.common.util.PromptLoader;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DeepSeek 文本模型客户端：负责简历与 JD 的语义匹配分析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient {

    private static final String SYSTEM_PROMPT = PromptLoader.get("deepseek-match-analyze");

    private static final String POLISH_PROMPT = PromptLoader.get("deepseek-resume-polish");

    private static final String COVER_LETTER_PROMPT = PromptLoader.get("deepseek-cover-letter");

    /** 流式版本只需要纯文本正文，不回 JSON，前端才能逐字渲染 */
    private static final String COVER_LETTER_STREAM_PROMPT = PromptLoader.get("deepseek-cover-letter-stream");

    private static final String CAREER_PROMPT = PromptLoader.get("deepseek-career-advice");

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

    /** 求职信流式生成：逐段回调正文，返回完整文本（不包 JSON，便于前端逐字渲染）。 */
    public String writeCoverLetterStream(String resumeSummary, String jobName, String companyName,
                                         String jobRequirement, Consumer<String> onDelta) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.4);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "system", "content", COVER_LETTER_STREAM_PROMPT),
                Map.of("role", "user", "content", coverLetterUserPrompt(resumeSummary, jobName, companyName, jobRequirement))
        ));

        StringBuilder full = new StringBuilder();
        try {
            String url = baseUrl + "/chat/completions";
            restTemplate.execute(url, HttpMethod.POST,
                    request -> {
                        request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        request.getHeaders().setBearerAuth(apiKey);
                        objectMapper.writeValue(request.getBody(), body);
                    },
                    response -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) {
                                    continue;
                                }
                                String data = line.substring(5).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                JsonNode delta = objectMapper.readTree(data)
                                        .path("choices").path(0).path("delta").path("content");
                                if (delta.isMissingNode() || delta.isNull()) {
                                    continue;
                                }
                                String chunk = delta.asText();
                                if (StringUtils.hasText(chunk)) {
                                    full.append(chunk);
                                    if (onDelta != null) {
                                        onDelta.accept(chunk);
                                    }
                                }
                            }
                        }
                        return null;
                    });
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("DeepSeek 流式生成失败", e);
            throw new BusinessException("求职信生成失败，请稍后重试");
        }
        return AiSafetyGuard.sanitizeOutput(full.toString());
    }

    private String coverLetterUserPrompt(String resumeSummary, String jobName, String companyName, String jobRequirement) {
        return "【目标岗位】\n" + (jobName == null ? "" : jobName)
                + "\n【目标公司】\n" + (companyName == null ? "" : companyName)
                + "\n\n【岗位要求】\n" + AiSafetyGuard.wrapUntrusted(jobRequirement)
                + "\n\n【候选人简历】\n" + resumeSummary
                + "\n\n请只使用与岗位要求相关的简历内容，无关内容不要写入。";
    }

    /** 针对目标岗位撰写中文求职信，返回 JSON 字符串。 */
    public String writeCoverLetter(String resumeSummary, String jobName, String companyName, String jobRequirement) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY");
        }
        return chatJson(COVER_LETTER_PROMPT,
                coverLetterUserPrompt(resumeSummary, jobName, companyName, jobRequirement), 0.4);
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

    /** 去除 Markdown 代码块并做输出脱敏，DeepSeek 的返回统一经过这里。 */
    private String stripCodeFences(String content) {
        return AiSafetyGuard.sanitizeOutput(LlmText.stripCodeFences(content));
    }
}