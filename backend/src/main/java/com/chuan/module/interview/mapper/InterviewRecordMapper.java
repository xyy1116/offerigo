package com.chuan.module.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.interview.entity.InterviewRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 面试记录 Mapper。
 */
@Mapper
public interface InterviewRecordMapper extends BaseMapper<InterviewRecord> {
}