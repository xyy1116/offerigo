package com.chuan.common.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * 阿里云 Paraformer 实时语音识别客户端：通过 WebSocket 桥接浏览器 PCM 音频流。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParaformerRealtimeClient {

    private final ObjectMapper objectMapper;

    @Value("${llm.paraformer.ws-url}")
    private String wsUrl;

    @Value("${llm.paraformer.api-key}")
    private String apiKey;

    @Value("${llm.paraformer.model}")
    private String model;

    @Value("${llm.paraformer.language-hints:zh}")
    private String languageHints;

    /** 百炼控制台创建的热词表 ID（专业术语提权），为空则不启用 */
    @Value("${llm.paraformer.vocabulary-id:}")
    private String vocabularyId;

    @Value("${llm.paraformer.disfluency-removal-enabled:true}")
    private boolean disfluencyRemovalEnabled;

    @Value("${llm.paraformer.max-sentence-silence:800}")
    private int maxSentenceSilence;

    @Value("${llm.paraformer.multi-threshold-mode-enabled:true}")
    private boolean multiThresholdModeEnabled;

    @Value("${llm.paraformer.heartbeat:true}")
    private boolean heartbeat;

    /** 语义标点：自动补充句读，提升中英混合句子的可读性 */
    @Value("${llm.paraformer.semantic-punctuation-enabled:true}")
    private boolean semanticPunctuationEnabled;

    /** 标点预测 */
    @Value("${llm.paraformer.punctuation-prediction-enabled:true}")
    private boolean punctuationPredictionEnabled;

    /** 逆文本正则化：数字/单位等口语化表达转为规范文本 */
    @Value("${llm.paraformer.inverse-text-normalization-enabled:true}")
    private boolean inverseTextNormalizationEnabled;

    /** 识别回调。 */
    public interface AsrListener {
        /** 任务已就绪，可以开始推送音频。 */
        void onReady();

        /** 识别到文本；sentenceEnd 为 true 表示整句结束。 */
        void onResult(String text, boolean sentenceEnd);

        void onError(String message);
    }

    /** 一次语音识别会话。 */
    public AsrSession connect(AsrListener listener) {
        return new AsrSession(listener);
    }

    public class AsrSession {

        private final AsrListener listener;
        private final String taskId;
        private WebSocket socket;
        private volatile boolean ready = false;

        AsrSession(AsrListener listener) {
            this.listener = listener;
            this.taskId = UUID.randomUUID().toString().replace("-", "");
        }

        public void start() {
            HttpClient client = HttpClient.newHttpClient();
            WebSocket.Listener wsListener = new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket webSocket) {
                    socket = webSocket;
                    try {
                        webSocket.sendText(runTaskJson(), true);
                    } catch (Exception e) {
                        log.error("发送 run-task 失败", e);
                        listener.onError("ASR 任务启动失败");
                    }
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    handleText(data.toString());
                    webSocket.request(1);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                    ready = false;
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    ready = false;
                    log.warn("Paraformer WebSocket 出错: {}", error.getMessage());
                }
            };

            try {
                client.newWebSocketBuilder()
                        .header("Authorization", "Bearer " + apiKey)
                        .buildAsync(URI.create(wsUrl), wsListener);
            } catch (Exception e) {
                log.error("连接 Paraformer 失败", e);
                listener.onError("语音识别连接失败");
            }
        }

        public void sendAudio(byte[] pcm) {
            WebSocket ws = this.socket;
            if (ws != null && ready) {
                // 非阻塞推送：避免阻塞浏览器 WebSocket 回读线程，提升连续语音吞吐与响应速度
                ws.sendBinary(ByteBuffer.wrap(pcm), true)
                        .exceptionally(e -> {
                            log.warn("推送音频失败: {}", e.getMessage());
                            return null;
                        });
            }
        }

        public void finish() {
            WebSocket ws = this.socket;
            if (ws != null && ready) {
                try {
                    ws.sendText(finishTaskJson(), true);
                } catch (Exception e) {
                    log.warn("发送 finish-task 失败: {}", e.getMessage());
                }
            }
        }

        public void close() {
            WebSocket ws = this.socket;
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
                } catch (Exception e) {
                    // 忽略
                }
            }
            ready = false;
        }

        private void handleText(String text) {
            try {
                JsonNode root = objectMapper.readTree(text);
                JsonNode header = root.path("header");
                String event = header.path("event").asText("");
                if (event.isEmpty()) {
                    event = header.path("action").asText("");
                }
                switch (event) {
                    case "task-started" -> {
                        ready = true;
                        listener.onReady();
                    }
                    case "result-generated" -> {
                        JsonNode sentence = root.path("payload").path("output").path("sentence");
                        String stext = sentence.path("text").asText("");
                        boolean end = sentence.path("sentence_end").asBoolean(false);
                        if (stext != null && !stext.isEmpty()) {
                            listener.onResult(stext, end);
                        }
                    }
                    case "task-failed" -> {
                        String msg = header.path("error_message").asText("语音识别失败");
                        listener.onError(msg);
                    }
                    default -> {
                        // task-finished 等，忽略
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Paraformer 响应失败: {}", text, e);
            }
        }

        private String runTaskJson() throws Exception {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("action", "run-task");
            header.put("task_id", taskId);
            header.put("streaming", "duplex");

            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("format", "pcm");
            parameters.put("sample_rate", 16000);
            parameters.put("language_hints", Arrays.stream(languageHints.split(","))
                    .map(String::trim).filter(StringUtils::hasText).collect(Collectors.toList()));
            parameters.put("disfluency_removal_enabled", disfluencyRemovalEnabled);
            parameters.put("max_sentence_silence", maxSentenceSilence);
            parameters.put("multi_threshold_mode_enabled", multiThresholdModeEnabled);
            parameters.put("heartbeat", heartbeat);
            parameters.put("semantic_punctuation_enabled", semanticPunctuationEnabled);
            parameters.put("punctuation_prediction_enabled", punctuationPredictionEnabled);
            parameters.put("inverse_text_normalization_enabled", inverseTextNormalizationEnabled);
            if (StringUtils.hasText(vocabularyId)) {
                parameters.put("vocabulary_id", vocabularyId);
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("task_group", "audio");
            payload.put("task", "asr");
            payload.put("function", "recognition");
            payload.put("model", model);
            payload.put("parameters", parameters);
            payload.put("input", Map.of());

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("header", header);
            msg.put("payload", payload);
            return objectMapper.writeValueAsString(msg);
        }

        private String finishTaskJson() throws Exception {
            Map<String, Object> header = new LinkedHashMap<>();
            header.put("action", "finish-task");
            header.put("task_id", taskId);
            header.put("streaming", "duplex");

            Map<String, Object> msg = new LinkedHashMap<>();
            msg.put("header", header);
            msg.put("payload", Map.of("input", Map.of()));
            return objectMapper.writeValueAsString(msg);
        }
    }
}