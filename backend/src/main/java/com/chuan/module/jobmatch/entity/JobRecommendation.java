package com.chuan.module.jobmatch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 旧版岗位推荐记录：仅用于兼容历史数据与投递渠道回填，不再由手动刷新生成。
 */
@Data
@TableName("job_recommendation")
public class JobRecommendation implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String jobName;

    private String companyName;

    /** 工作地点。 */
    private String location;

    /** 薪资范围。 */
    private String salary;

    /** 来源平台（BOSS直聘 / 智联招聘 / 猎聘 / 前程无忧 / 拉勾网 等）。 */
    private String sourcePlatform;

    /** 标准化后的岗位职责与任职要求全文（与我的目标岗位格式一致，可入 target_job）。 */
    private String jobRequirement;

    private String jobLink;

    /** 匹配度（0-100，保留一位小数），仅用于推荐排序。 */
    private BigDecimal matchScore;

    private LocalDate recommendDate;

    /** 是否为新推荐（未读）：1 是 / 0 否。 */
    private Integer isNew;

    /** 是否已加入我的目标岗位：1 是 / 0 否。 */
    private Integer added;

    /** 加入我的目标岗位后回填的岗位 ID。 */
    private Long targetJobId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}