package com.chuan.common.config;

import com.chuan.common.interceptor.JwtInterceptor;
import com.chuan.common.interceptor.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 拦截器与限流拦截器，并放行登录/注册等公开接口。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/captcha",
                        "/api/auth/forgot/**",
                        "/error"
                );
        // 排在 JWT 之后注册：这样限流可以按已解析出的 userId 区分用户
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**");
    }
}