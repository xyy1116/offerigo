package com.chuan.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptLoaderTest {

    /** 每新增一个 resources/prompts/*.md 模板，都要加进这里，避免打包漏文件。 */
    private static final List<String> TEMPLATES = List.of(
            "deepseek-match-analyze", "deepseek-resume-polish", "deepseek-cover-letter",
            "deepseek-cover-letter-stream", "deepseek-career-advice",
            "exam-techstack", "exam-generate", "exam-grade", "exam-real-answer",
            "jobmatch-evaluate", "jobmatch-hard-check", "jobmatch-fine-score", "jobmatch-resume",
            "interview-scoring");

    @Test
    void loadsEveryTemplateFromClasspath() {
        for (String name : TEMPLATES) {
            String text = PromptLoader.get(name);
            assertFalse(text.isBlank(), name + " 模板内容为空");
            assertTrue(text.length() > 100, name + " 模板疑似被截断");
        }
        assertSame(PromptLoader.get("exam-grade"), PromptLoader.get("exam-grade"), "模板应被缓存复用");
    }
}