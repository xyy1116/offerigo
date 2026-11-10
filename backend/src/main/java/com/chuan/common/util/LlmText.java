package com.chuan.common.util;

/**
 * 大模型返回文本的通用处理：Json 模式下模型偶尔仍会包一层 Markdown 代码块，
 * 解析前统一剥掉，避免三处客户端各写一份。
 */
public final class LlmText {

    private LlmText() {
    }

    /** 去掉 ``` 包裹，返回纯内容；null 返回空串。 */
    public static String stripCodeFences(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }
}