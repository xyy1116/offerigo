package com.chuan.module.jobmatch.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.jobmatch.entity.JobRecommendation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能岗位推荐 Mapper。
 */
@Mapper
public interface JobRecommendationMapper extends BaseMapper<JobRecommendation> {
}