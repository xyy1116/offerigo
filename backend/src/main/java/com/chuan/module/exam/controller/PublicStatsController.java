package com.chuan.module.exam.controller;

import com.chuan.common.result.Result;
import com.chuan.module.exam.mapper.ExamPaperMapper;
import com.chuan.module.exam.mapper.ExamQuestionBankMapper;
import com.chuan.module.exam.vo.PublicStatsVO;
import com.chuan.module.interview.mapper.InterviewRecordMapper;
import com.chuan.module.jobmatch.mapper.JobApplicationMapper;
import com.chuan.module.jobmatch.mapper.KnowledgeEntryMapper;
import com.chuan.module.jobmatch.mapper.TargetJobMapper;
import com.chuan.module.jobrecommend.mapper.CrawlJobMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 公开统计接口：首页展示平台规模，不需要登录。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicStatsController {

    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    private final ExamQuestionBankMapper bankMapper;
    private final CrawlJobMapper crawlJobMapper;
    private final TargetJobMapper targetJobMapper;
    private final KnowledgeEntryMapper knowledgeEntryMapper;
    private final InterviewRecordMapper interviewRecordMapper;
    private final ExamPaperMapper examPaperMapper;
    private final JobApplicationMapper jobApplicationMapper;

    private volatile PublicStatsVO cache;
    private volatile long cachedAt = 0;

    @GetMapping("/stats")
    public Result<PublicStatsVO> stats() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cachedAt > CACHE_TTL_MS) {
            synchronized (this) {
                if (cache == null || now - cachedAt > CACHE_TTL_MS) {
                    PublicStatsVO fresh = compute();
                    if (count(fresh) > 0) {
                        cache = fresh;
                        cachedAt = System.currentTimeMillis();
                    }
                    return Result.success(fresh);
                }
            }
        }
        return Result.success(cache);
    }

    private long count(PublicStatsVO vo) {
        return n(vo.getQuestionCount()) + n(vo.getJobCount())
                + n(vo.getMaterialCount()) + n(vo.getActivityCount());
    }

    private long n(Long value) {
        return value == null ? 0 : value;
    }

    private PublicStatsVO compute() {
        PublicStatsVO vo = new PublicStatsVO();
        vo.setQuestionCount(n(bankMapper.selectCount(null)));
        vo.setJobCount(n(crawlJobMapper.selectCount(null)) + n(targetJobMapper.selectCount(null)));
        vo.setMaterialCount(n(knowledgeEntryMapper.selectCount(null)));
        vo.setActivityCount(n(interviewRecordMapper.selectCount(null))
                + n(examPaperMapper.selectCount(null))
                + n(jobApplicationMapper.selectCount(null)));
        return vo;
    }
}