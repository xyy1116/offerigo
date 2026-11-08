package com.chuan.common.interceptor;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.result.ResultCode;
import com.chuan.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：校验请求头中的令牌，并将用户信息写入 request 属性。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行 CORS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(jwtUtil.getHeader());
        if (!StringUtils.hasText(header) || !header.startsWith(jwtUtil.getTokenPrefix())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        String token = header.substring(jwtUtil.getTokenPrefix().length()).trim();
        try {
            request.setAttribute("userId", jwtUtil.getUserId(token));
            request.setAttribute("username", jwtUtil.getUsername(token));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return true;
    }
}