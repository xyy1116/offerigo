package com.chuan.common.interceptor;

import com.chuan.common.exception.BusinessException;
import com.chuan.common.ratelimit.SlidingWindowLimiter;
import com.chuan.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口限流拦截器：登录类接口按 IP 严控，其余接口按「IP + 用户」放宽。
 *
 * <p>注册在 JwtInterceptor 之后，这样才能拿到 userId 做维度区分。
 */
@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /**
     * 登录/注册/找回密码：1 分钟 20 次。
     * 多人可能共用同一出口 IP（校园网 NAT），阈值太低会把正常登录挡在门外。
     */
    private static final SlidingWindowLimiter AUTH_LIMITER = new SlidingWindowLimiter(20, 60_000);

    /** 普通接口：1 分钟 600 次。要容得下生成任务的多路进度轮询，只挡脚本化的高频刷取。 */
    private static final SlidingWindowLimiter API_LIMITER = new SlidingWindowLimiter(600, 60_000);

    /** AI 生成类接口：1 分钟 15 次/用户。既够演示，也避免模型调用量被刷爆。 */
    private static final SlidingWindowLimiter AI_LIMITER = new SlidingWindowLimiter(15, 60_000);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String ip = clientIp(request);
        String userKey = ip + "|" + request.getAttribute("userId");
        boolean allowed;
        if (isAuthPath(path)) {
            allowed = AUTH_LIMITER.tryAcquire(ip);
        } else if (isAiPath(path)) {
            allowed = AI_LIMITER.tryAcquire(userKey) && API_LIMITER.tryAcquire(userKey);
        } else {
            allowed = API_LIMITER.tryAcquire(userKey);
        }
        if (!allowed) {
            log.warn("触发限流：path={}, ip={}", path, ip);
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS);
        }
        return true;
    }

    /** 会触发大模型调用的路径；前端轮询用的轻量校验排除在外 */
    private boolean isAiPath(String path) {
        if (path.contains("/analyze/verify")) {
            return false;
        }
        return path.contains("/generate")
                || path.contains("/analyze")
                || path.contains("/cover-letter")
                || path.contains("/tailor")
                || path.contains("/evaluate")
                || path.contains("/ats-check")
                || path.contains("/knowledge/split")
                || path.contains("/knowledge/generate-material");
    }

    private boolean isAuthPath(String path) {
        return path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/forgot");
    }

    // shortcut: 这里信任反向代理注入的 X-Real-IP / X-Forwarded-For，直连暴露端口时可被伪造绕过，
    // 上线时后端只通过内网暴露给 nginx。
    private String clientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}