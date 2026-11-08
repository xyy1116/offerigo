package com.chuan.module.exam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chuan.common.result.Result;
import com.chuan.module.exam.entity.ExamQuestionBank;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.mapper.ExamQuestionSourceMapper;
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
 * 公开统计接口：首页展示平台规模，不需要登录。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final ExamQuestionBankMapper bankMapper;
    private final ExamQuestionSourceMapper sourceMapper;

    private volatile PublicStatsVO cache;
    private volatile long cachedAt = 0;

    @GetMapping("/stats")
    public Result<PublicStatsVO> stats() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cachedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || now - cachedAt > CACHE_TTL_MS) {
                    PublicStatsVO fresh = compute();
                    // 启动早期可能出现空表：0 值不缓存，下次请求重新统计
                    if (fresh.getQuestionCount() != null && fresh.getQuestionCount() > 0) {
                        cache = fresh;
                        cachedAt = System.currentTimeMillis();
                    }
                    return Result.success(fresh);
                }
            }
        }
        return Result.success(cache);
    }

    private PublicStatsVO compute() {
        // 必须带上主键列，避免整行都为 NULL 时被 MyBatis 映射成 null 对象
        List<ExamQuestionBank> rows = bankMapper.selectList(
                new LambdaQueryWrapper<ExamQuestionBank>()
                        .select(ExamQuestionBank::getId, ExamQuestionBank::getCompany, ExamQuestionBank::getTechTags));
        Set<String> companies = new HashSet<>();
        Set<String> tags = new HashSet<>();
        for (ExamQuestionBank row : rows) {
            if (row == null) {
                continue;
            }
            addTokens(companies, row.getCompany());
            addTokens(tags, row.getTechTags());
        }
        PublicStatsVO vo = new PublicStatsVO();
        vo.setQuestionCount((long) rows.size());
        vo.setCompanyCount(companies.size());
        vo.setTagCount(tags.size());
        Long sources = sourceMapper.selectCount(null);
        vo.setSourceCount(sources == null ? 0 : sources.intValue());
        return vo;
    }

    /** 按常见分隔符拆成去重标签，跳过「多家大厂」这种占位值。 */
    private void addTokens(Set<String> target, String raw) {
        if (!StringUtils.hasText(raw)) {
            return;
        }
        for (String token : raw.split("[,，、;；/]+")) {
            String name = token.trim();
            if (StringUtils.hasText(name) && !"多家大厂".equals(name)) {
                target.add(name);
            }
        }
    }
}