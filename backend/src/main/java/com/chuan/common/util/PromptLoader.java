package com.chuan.common.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载：从 classpath:prompts/&lt;name&gt;.md 读取原文，首次加载后缓存。
 * 模板与调用代码分离，改提示词不需要动 Java，便于评审与迭代。
 */
public final class PromptLoader {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PromptLoader() {
    }

    public static String get(String name) {
        return CACHE.computeIfAbsent(name, key -> {
            String path = "/prompts/" + key + ".md";
            try (InputStream in = PromptLoader.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("缺少 Prompt 模板: " + path);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("读取 Prompt 模板失败: " + path, e);
            }
        });
    }
}