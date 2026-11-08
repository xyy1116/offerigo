package com.chuan.common.llm;

import com.chuan.common.exception.BusinessException;
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

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 通义千问视觉模型客户端：负责 JD 截图文字识别（OCR）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QwenVisionClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.qwen-vl.base-url}")
    private String baseUrl;

    @Value("${llm.qwen-vl.api-key}")
    private String apiKey;

    @Value("${llm.qwen-vl.model}")
    private String model;

    public String ocr(byte[] imageBytes, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        String mime = StringUtils.hasText(contentType) ? contentType : "image/jpeg";
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text",
                                        "请识别并原样输出图片中的岗位 JD 正文文字内容。忽略页眉、页脚、页码、水印等非正文内容，不要添加任何解释或前缀。")
                        )
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return extractText(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通义千问 OCR 调用失败", e);
            throw new BusinessException("JD 截图识别失败，请稍后重试");
        }
    }

    /** 简历图片 OCR：识别扫描版/图片型 PDF 页面中的简历文字内容。 */
    public String ocrResume(byte[] imageBytes, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        String mime = StringUtils.hasText(contentType) ? contentType : "image/png";
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text",
                                        "请完整识别图片中的简历正文文字内容，按原样输出。忽略页眉、页脚、页码、水印、投递按钮等非正文内容，不要添加任何解释或前缀。")
                        )
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return extractText(content);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通义千问简历图片识别失败", e);
            throw new BusinessException("简历图片识别失败，请稍后重试");
        }
    }

    /** JD 截图识别：识别全文并提取核心技术栈，返回 JSON 字符串 {"text":"...","techStack":[...]}。 */
    public String ocrJobJson(byte[] imageBytes, String contentType) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        String mime = StringUtils.hasText(contentType) ? contentType : "image/jpeg";
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "image_url",
                                        "image_url", Map.of("url", dataUrl)),
                                Map.of("type", "text", "text",
                                        "请完整识别图片中的岗位 JD 正文文字内容（忽略页眉、页脚、页码、水印），并从中提取核心技术栈/技能关键词。"
                                        + "严格按 JSON 格式输出，不要输出 JSON 以外的任何内容，不要使用 Markdown 代码块。"
                                        + "结构如下：{\"text\":\"完整识别出的 JD 文字\",\"techStack\":[\"技术栈1\",\"技术栈2\"]}，"
                                        + "其中 techStack 只包含图片中明确出现的编程语言、框架、中间件、工具、平台等技术名词。")
                        )
                ))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return stripFences(extractText(content));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("通义千问 JD 截图识别失败", e);
            throw new BusinessException("JD 截图识别失败，请稍后重试");
        }
    }

    private String stripFences(String content) {
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

    private String extractText(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText()) || part.has("text")) {
                    sb.append(part.path("text").asText());
                }
            }
            return sb.toString();
        }
        return "";
    }
}