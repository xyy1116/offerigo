package com.chuan.module.interview.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chuan.common.exception.BusinessException;
import com.chuan.module.interview.entity.InterviewRecord;
import com.chuan.module.interview.mapper.InterviewRecordMapper;
import com.chuan.module.interview.service.InterviewService;
import com.chuan.module.interview.vo.InterviewRecordVO;
import com.chuan.module.interview.vo.InterviewReportVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 面试服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl extends ServiceImpl<InterviewRecordMapper, InterviewRecord> implements InterviewService {

    private final ObjectMapper objectMapper;

    @Override
    public List<InterviewRecordVO> history(Long userId) {
        // 仅取最近 10 条，配合 idx_user_time(user_id, create_time) 索引，降低数据库查询负载
        List<InterviewRecord> records = list(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getUserId, userId)
                .orderByDesc(InterviewRecord::getCreateTime)
                .last("LIMIT 10"));
        List<InterviewRecordVO> result = new ArrayList<>();
        for (InterviewRecord r : records) {
            InterviewRecordVO vo = new InterviewRecordVO();
            vo.setId(r.getId());
            vo.setPosition(r.getPosition());
            vo.setScore(r.getScore());
            vo.setCreateTime(r.getCreateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    public InterviewRecordVO detail(Long id, Long userId) {
        InterviewRecord r = getById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException("面试记录不存在");
        }
        InterviewRecordVO vo = new InterviewRecordVO();
        vo.setId(r.getId());
        vo.setPosition(r.getPosition());
        vo.setScore(r.getScore());
        vo.setCreateTime(r.getCreateTime());
        vo.setTranscript(r.getTranscript());
        if (StringUtils.hasText(r.getReportJson())) {
            try {
                vo.setReport(objectMapper.readValue(r.getReportJson(), InterviewReportVO.class));
            } catch (Exception e) {
                log.warn("评估报告解析失败, id={}", id, e);
            }
        }
        return vo;
    }

    @Override
    public void deleteRecord(Long id, Long userId) {
        InterviewRecord r = getById(id);
        if (r == null || !r.getUserId().equals(userId)) {
            throw new BusinessException("面试记录不存在");
        }
        removeById(id);
    }

    @Override
    public Long saveRecord(Long userId, String position, String transcript, InterviewReportVO report, BigDecimal score) {
        try {
            InterviewRecord r = new InterviewRecord();
            r.setUserId(userId);
            r.setPosition(position);
            r.setStatus("done");
            r.setTranscript(transcript);
            r.setReportJson(objectMapper.writeValueAsString(report));
            r.setScore(score);
            LocalDateTime now = LocalDateTime.now();
            r.setCreateTime(now);
            r.setUpdateTime(now);
            save(r);
            return r.getId();
        } catch (Exception e) {
            log.error("保存面试记录失败, userId={}", userId, e);
            throw new BusinessException("保存面试记录失败");
        }
    }

    @Override
    public void deleteInProgressSnapshot(Long userId) {
        remove(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getUserId, userId)
                .eq(InterviewRecord::getStatus, "in_progress"));
    }

    @Override
    public Long saveSnapshot(Long userId, String position, String jdText, String techStack,
                             String questionBankKey, String stage, Integer stageQuestionCount,
                             Integer questionCount, String transcript) {
        LocalDateTime now = LocalDateTime.now();
        InterviewRecord existing = getOne(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getUserId, userId)
                .eq(InterviewRecord::getStatus, "in_progress")
                .last("LIMIT 1"), false);
        if (existing != null) {
            existing.setPosition(position);
            existing.setJdText(jdText);
            existing.setTechStack(techStack);
            existing.setQuestionBankKey(questionBankKey);
            existing.setStage(stage);
            existing.setStageQuestionCount(stageQuestionCount);
            existing.setQuestionCount(questionCount);
            existing.setTranscript(transcript);
            existing.setUpdateTime(now);
            updateById(existing);
            return existing.getId();
        }
        InterviewRecord r = new InterviewRecord();
        r.setUserId(userId);
        r.setPosition(position);
        r.setJdText(jdText);
        r.setTechStack(techStack);
        r.setQuestionBankKey(questionBankKey);
        r.setStage(stage);
        r.setStageQuestionCount(stageQuestionCount);
        r.setQuestionCount(questionCount);
        r.setStatus("in_progress");
        r.setTranscript(transcript);
        r.setCreateTime(now);
        r.setUpdateTime(now);
        save(r);
        return r.getId();
    }

    @Override
    public InterviewRecord getActive(Long userId) {
        return getOne(new LambdaQueryWrapper<InterviewRecord>()
                .eq(InterviewRecord::getUserId, userId)
                .eq(InterviewRecord::getStatus, "in_progress")
                .orderByDesc(InterviewRecord::getUpdateTime)
                .last("LIMIT 1"), false);
    }

    @Override
    public void finalizeRecord(Long recordId, String transcript, InterviewReportVO report, BigDecimal score) {
        InterviewRecord r = getById(recordId);
        if (r == null) {
            return;
        }
        try {
            r.setStatus("done");
            r.setStage(null);
            r.setStageQuestionCount(null);
            r.setTranscript(transcript);
            r.setReportJson(objectMapper.writeValueAsString(report));
            r.setScore(score);
            r.setUpdateTime(LocalDateTime.now());
            updateById(r);
        } catch (Exception e) {
            log.error("完成面试记录失败, id={}", recordId, e);
            throw new BusinessException("完成面试记录失败");
        }
    }
}