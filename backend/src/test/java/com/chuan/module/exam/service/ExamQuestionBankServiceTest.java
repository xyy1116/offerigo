package com.chuan.module.exam.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.chuan.common.llm.ExamClient;
import com.chuan.module.exam.crawler.ExamQuestionCrawler;
import com.chuan.module.exam.entity.ExamQuestionBank;
import com.chuan.module.exam.entity.ExamQuestionUsage;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.mapper.ExamQuestionSourceMapper;
import com.chuan.module.exam.mapper.ExamQuestionUsageMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExamQuestionBankServiceTest {

    @Test
    void prefersUnusedQuestionsEvenWhenUsedOneHasHigherScore() {
        ExamQuestionBankMapper bankMapper = mock(ExamQuestionBankMapper.class);
        ExamQuestionSourceMapper sourceMapper = mock(ExamQuestionSourceMapper.class);
        ExamQuestionUsageMapper usageMapper = mock(ExamQuestionUsageMapper.class);
        ExamQuestionCrawler crawler = mock(ExamQuestionCrawler.class);
        ExamClient examClient = mock(ExamClient.class);

        ExamQuestionBank used = bank("h1", "已做过的 Java 题", 60);
        ExamQuestionBank unused = bank("h2", "没做过的 Java 题", 1);
        when(bankMapper.selectList(any(Wrapper.class))).thenReturn(List.of(used, unused));

        ExamQuestionUsage usage = new ExamQuestionUsage();
        usage.setUserId(1L);
        usage.setQuestionHash("h1");
        usage.setLastUsedTime(LocalDateTime.now());
        when(usageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(usage));

        ExamQuestionBankService service = new ExamQuestionBankService(
                bankMapper, sourceMapper, usageMapper, crawler, examClient);
        List<ExamQuestionBank> picked = service.pick("Java后端开发工程师", List.of("Java"), null, null, 1, 1L, false, 0);

        assertEquals(1, picked.size());
        assertEquals("h2", picked.get(0).getQuestionHash());
    }

    @Test
    void javaDoesNotMatchJavaScriptTag() {
        ExamQuestionBankMapper bankMapper = mock(ExamQuestionBankMapper.class);
        ExamQuestionSourceMapper sourceMapper = mock(ExamQuestionSourceMapper.class);
        ExamQuestionUsageMapper usageMapper = mock(ExamQuestionUsageMapper.class);
        ExamQuestionCrawler crawler = mock(ExamQuestionCrawler.class);
        ExamClient examClient = mock(ExamClient.class);

        ExamQuestionBank java = bank("java-1", "熟悉Java多线程与JVM", 1);
        java.setTechTags("Java");
        java.setRoleCategory("后端");
        ExamQuestionBank js = bank("js-1", "JavaScript 事件循环与闭包", 60);
        js.setTechTags("JavaScript");
        js.setRoleCategory("前端");
        when(bankMapper.selectList(any(Wrapper.class))).thenReturn(List.of(java, js));
        when(usageMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        ExamQuestionBankService service = new ExamQuestionBankService(
                bankMapper, sourceMapper, usageMapper, crawler, examClient);
        List<ExamQuestionBank> picked = service.pick("Java后端开发工程师", List.of("Java"),
                null, null, 1, 1L, false, 0);

        assertEquals(1, picked.size());
        assertEquals("java-1", picked.get(0).getQuestionHash());
    }

    private ExamQuestionBank bank(String hash, String stem, int frequency) {
        ExamQuestionBank bank = new ExamQuestionBank();
        bank.setQuestionHash(hash);
        bank.setStem(stem);
        bank.setCompany("字节跳动");
        bank.setRoleCategory("后端");
        bank.setTechTags("Java");
        bank.setQuestionType("short_answer");
        bank.setDifficulty("middle");
        bank.setFrequency(frequency);
        return bank;
    }
}