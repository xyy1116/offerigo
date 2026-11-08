package com.chuan.module.jobrecommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuan.module.jobrecommend.entity.UserTargetJob;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserTargetJobMapper extends BaseMapper<UserTargetJob> {

    /** 依赖 uk_user_job 唯一索引去重；重复添加返回 0，不抛异常。 */
    @Insert("INSERT IGNORE INTO user_target_job (user_id, crawl_job_id, create_time) "
            + "VALUES (#{userId}, #{crawlJobId}, NOW())")
    int insertIgnore(@Param("userId") Long userId, @Param("crawlJobId") Long crawlJobId);
}