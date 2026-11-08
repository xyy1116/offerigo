package com.chuan.common.util;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 岗位要求确定性排版净化：统一分点符号、压缩空白与空行，不改动文字内容。
 */
public final class RequirementText {

    /** 行首非标准分点符号：星号/几何符号/数字序号/圈号等（必须命中真实项目符号，避免误伤普通缩进行）。 */
    private static final Pattern LEADING_BULLET = Pattern.compile(
            "^\\s*(?:[*●■★✓◆◇▪▫◦►➤·•\\-–—_]+\\s*|\\d{1,2}[.、)）]\\s*|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]\\s*)");

    private RequirementText() {
    }

    public static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.replaceAll("\\*", "").split("[\\r\\n]");
        List<String> out = new ArrayList<>(lines.length);
        boolean previousBlank = false;
        for (String raw : lines) {
            String line = raw.trim().replaceAll(" {2,}", " ");
            if (line.isEmpty()) {
                // 压缩连续空行：仅当上一行非空且已有内容时保留一个空行
                if (!out.isEmpty() && !previousBlank) {
                    out.add("");
                    previousBlank = true;
                }
                continue;
            }
            if (LEADING_BULLET.matcher(line).find()) {
                line = LEADING_BULLET.matcher(line).replaceAll("").trim();
                line = "- " + line;
            }
            out.add(line);
            previousBlank = false;
        }
        // 去除首尾空行
        int start = 0;
        int end = out.size();
        while (start < end && out.get(start).isEmpty()) {
            start++;
        }
        while (end > start && out.get(end - 1).isEmpty()) {
            end--;
        }
        return String.join("\n", out.subList(start, end));
    }
}