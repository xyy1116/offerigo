package com.chuan.module.jobmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.jobmatch.entity.JobMatchResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评估结果缓存 Mapper。
 */
@Mapper
public interface JobMatchResultMapper extends BaseMapper<JobMatchResult> {
}