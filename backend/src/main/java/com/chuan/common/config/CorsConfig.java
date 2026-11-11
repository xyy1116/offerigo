package com.chuan.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：开发阶段前端通过 Vite 代理访问后端，此配置用于直连或生产环境分离部署场景。
 *
 * <p>浏览器对同源 POST 也会带上 Origin 头，所以生产环境的公网地址必须加进白名单，
 * 否则登录等写操作会被 CORS 拒绝（表现为 403）。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 逗号分隔的来源模式，生产环境通过 CORS_ALLOWED_ORIGINS 注入 */
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}