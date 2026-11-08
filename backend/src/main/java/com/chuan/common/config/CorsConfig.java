package com.chuan.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：开发阶段前端通过 Vite 代理访问后端，此配置用于直连或生产环境分离部署场景。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                // 开发环境（Vite dev server 默认 5173 / 3000）+ 允许通过 Origin 请求头自定义
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // 生产环境部署时：allowedOriginPatterns 只保留生产域名，allowCredentials 保持 true
                .allowCredentials(true)
                .maxAge(3600);
    }
}