package com.chuan.module.interview.ws;

import com.chuan.module.interview.service.InterviewOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.nio.ByteBuffer;

/**
 * AI 面试官 WebSocket 处理器：承载浏览器音频流与对话消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebSocketHandler extends AbstractWebSocketHandler {

    private final InterviewOrchestrator orchestrator;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignore) {
            }
            return;
        }
        orchestrator.onConnected(session, userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        orchestrator.onText(session, message.getPayload());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer buf = message.getPayload();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        orchestrator.onBinary(session, bytes);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        orchestrator.onDisconnected(session);
    }
}