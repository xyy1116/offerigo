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

import java.net.URI;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CosyVoice 语音合成客户端：将面试官回复文本合成为 mp3 音频。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CosyVoiceClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.cosyvoice.base-url}")
    private String baseUrl;

    @Value("${llm.cosyvoice.api-key}")
    private String apiKey;

    @Value("${llm.cosyvoice.model}")
    private String model;

    @Value("${llm.cosyvoice.voice}")
    private String voice;

    @Value("${llm.cosyvoice.format}")
    private String format;

    @Value("${llm.cosyvoice.sample-rate}")
    private Integer sampleRate;

    @Value("${llm.cosyvoice.rate:1.0}")
    private Double rate;

    public byte[] synthesize(String text) {
        if (!StringUtils.hasText(apiKey)) {
            throw new BusinessException("未配置通义千问 API Key，请设置环境变量 DASHSCOPE_API_KEY");
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", text);
        input.put("voice", voice);
        input.put("format", format);
        input.put("sample_rate", sampleRate);
        input.put("rate", rate);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", input);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode audio = objectMapper.readTree(response.getBody()).path("output").path("audio");
            // 优先 base64 数据，其次 24h 有效 URL
            String data = audio.path("data").asText(null);
            if (StringUtils.hasText(data)) {
                return Base64.getDecoder().decode(data);
            }
            String url = audio.path("url").asText(null);
            if (StringUtils.hasText(url)) {
                // 用 URI 重载避免 UriTemplateHandler 对签名 URL 中的 %2B/%3D 等做二次编码，导致 OSS 403 SignatureDoesNotMatch
                ResponseEntity<byte[]> audioResp = restTemplate.exchange(
                        URI.create(url), HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
                return audioResp.getBody();
            }
            throw new BusinessException("语音合成返回为空");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("CosyVoice 语音合成失败", e);
            throw new BusinessException("语音合成失败");
        }
    }
}