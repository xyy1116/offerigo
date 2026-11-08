package com.chuan.module.interview.ws;

import com.chuan.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.Map;

/**
 * 面试 WebSocket 握手拦截器：从查询参数 token 校验 JWT。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        String token = null;
        if (StringUtils.hasText(query)) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("token=")) {
                    token = pair.substring("token=".length());
                    break;
                }
            }
        }
        if (!StringUtils.hasText(token)) {
            return false;
        }
        try {
            token = URLDecoder.decode(token, StandardCharsets.UTF_8);
            attributes.put("userId", jwtUtil.getUserId(token));
            attributes.put("username", jwtUtil.getUsername(token));
            return true;
        } catch (Exception e) {
            log.warn("WebSocket 握手 token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}