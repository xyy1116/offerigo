package com.chuan.module.jobrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户目标岗位关联：uk_user_job(user_id, crawl_job_id) 保证同一岗位只加入一次。
 */
@Data
@TableName("user_target_job")
public class UserTargetJob implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long crawlJobId;

    private LocalDateTime createTime;
}