package com.chuan.common.llm;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本向量客户端：调用百炼 OpenAI 兼容 /embeddings 接口。
 *
 * <p>Embedding 失败只返回空向量，不影响岗位入库与推荐列表展示；
 * 推荐时会再次尝试补齐缺失向量。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    /** 百炼兼容接口单次 input 不宜过大，按 10 条分片。 */
    private static final int BATCH_SIZE = 10;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${llm.embedding.model:text-embedding-v3}")
    private String model;

    @Value("${llm.embedding.api-key:}")
    private String apiKey;

    /** 未单独配置 embedding key 时复用通义千问的百炼 key。 */
    @Value("${llm.qwen-chat.api-key:}")
    private String qwenApiKey;

    public double[] embed(String text) {
        if (!StringUtils.hasText(text)) {
            return new double[0];
        }
        List<double[]> vectors = embedBatch(List.of(text));
        if (vectors.isEmpty() || vectors.get(0) == null) {
            return new double[0];
        }
        return vectors.get(0);
    }

    /**
     * 批量向量。返回列表与入参一一对应，失败的条目为 null，调用方可按索引对齐。
     */
    public List<double[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        List<double[]> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(texts.size(), start + BATCH_SIZE);
            List<String> chunk = texts.subList(start, end);
            List<double[]> part = request(chunk);
            for (int i = 0; i < part.size() && i < chunk.size(); i++) {
                result.set(start + i, part.get(i));
            }
        }
        return result;
    }

    private List<double[]> request(List<String> inputs) {
        List<double[]> empty = new ArrayList<>(Collections.nCopies(inputs.size(), null));
        String key = StringUtils.hasText(apiKey) ? apiKey : qwenApiKey;
        if (!StringUtils.hasText(key)) {
            log.warn("未配置 Embedding API Key，向量匹配将返回 0");
            return empty;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", inputs);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(key);

            String url = baseUrl.endsWith("/") ? baseUrl + "embeddings" : baseUrl + "/embeddings";
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            JsonNode data = objectMapper.readTree(response.getBody()).path("data");
            List<double[]> vectors = new ArrayList<>(Collections.nCopies(inputs.size(), null));
            int fallbackIndex = 0;
            for (JsonNode item : data) {
                int index = item.path("index").asInt(fallbackIndex);
                fallbackIndex++;
                JsonNode embedding = item.path("embedding");
                if (!embedding.isArray()) {
                    continue;
                }
                double[] vector = new double[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.get(i).asDouble();
                }
                if (index >= 0 && index < vectors.size()) {
                    vectors.set(index, vector);
                }
            }
            return vectors;
        } catch (Exception e) {
            log.warn("Embedding 接口调用失败：{}", e.getMessage());
            return empty;
        }
    }
}