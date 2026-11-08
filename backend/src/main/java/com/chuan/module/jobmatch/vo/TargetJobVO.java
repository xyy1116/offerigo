package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 目标岗位视图。
 */
@Data
public class TargetJobVO implements Serializable {

    private Long id;

    private String jobName;

    private String jobRequirement;

    private String companyName;

    private String email;

    /** 工作地点。 */
    private String location;

    /** 薪资范围。 */
    private String salary;

    private String techStack;

    private String jobType;

    /** 来源平台（第三方招聘平台或官网来源），无来源时为空。 */
    private String sourcePlatform;

    private LocalDateTime createTime;
}