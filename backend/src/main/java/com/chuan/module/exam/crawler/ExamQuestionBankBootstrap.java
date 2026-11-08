package com.chuan.module.exam.crawler;

import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 首次启动且题库为空时后台拉取一次真题，避免用户第一次进入笔试时只有 AI 题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExamQuestionBankBootstrap implements ApplicationRunner {

    private static final int MIN_BANK_SIZE = 20;

    private final ExamQuestionBankMapper bankMapper;
    private final ExamQuestionCrawler crawler;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (bankMapper.selectCount(null) < MIN_BANK_SIZE) {
                log.info("真题目库为空，开始后台初始化题库");
                crawler.crawlAllAsync();
            }
        } catch (Exception e) {
            log.warn("真题目库初始化检查失败：{}", e.getMessage());
        }
    }
}