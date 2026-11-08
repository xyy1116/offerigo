package com.chuan.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 通用 HTTP 客户端配置：为大模型调用提供带超时的 RestTemplate。
 * 读超时设为 180 秒，避免扫描件 PDF 高分辨率单页 OCR、长文本结构化提取等重调用被提前中断。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(180000);
        return new RestTemplate(factory);
    }
}