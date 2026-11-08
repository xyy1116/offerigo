package com.chuan.module.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.result.Result;
import com.chuan.module.exam.entity.ExamQuestionBank;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.vo.PublicStatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公开统计接口：首页展示题库规模，不需要登录。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final ExamQuestionBankMapper bankMapper;

    private volatile PublicStatsVO cache;
    private volatile long cachedAt = 0;

    @GetMapping("/stats")
    public Result<PublicStatsVO> stats() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cachedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || now - cachedAt > CACHE_TTL_MS) {
                    cache = compute();
                    cachedAt = System.currentTimeMillis();
                }
            }
        }
        return Result.success(cache);
    }

    private PublicStatsVO compute() {
        // 必须带主键列，避免 company 为空的行被 MyBatis 映射成 null 对象
        List<ExamQuestionBank> rows = bankMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionBank>()
                        .select(ExamQuestionBank::getId, ExamQuestionBank::getCompany));
        Set<String> companies = new HashSet<>();
        for (ExamQuestionBank row : rows) {
            if (row == null || !StringUtils.hasText(row.getCompany())) {
                continue;
            }
            for (String company : row.getCompany().split("、")) {
                String name = company.trim();
                if (StringUtils.hasText(name) && !"多家大厂".equals(name)) {
                    companies.add(name);
                }
            }
        }
        PublicStatsVO vo = new PublicStatsVO();
        vo.setQuestionCount((long) rows.size());
        vo.setCompanyCount(companies.size());
        return vo;
    }
}