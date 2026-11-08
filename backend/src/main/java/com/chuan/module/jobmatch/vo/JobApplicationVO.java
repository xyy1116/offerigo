package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递台账记录展示结构（草稿与正式记录共用）。
 */
@Data
public class JobApplicationVO implements Serializable {

    private Long id;

    private Long jobId;

    private String jobName;

    private String companyName;

    private LocalDateTime appliedAt;

    private String resumeVersion;

    /** 归档简历文件名；有文件时用于展示与下载。 */
    private String resumeFileName;

    /** 是否已归档简历 PDF（历史记录为 false）。 */
    private Boolean hasResume;

    private String jobLink;

    private String status;

    private String remark;

    /** 是否已到跟进提醒时间（投递满 N 天且未进入终态）。 */
    private Boolean followUpDue;

    /** 投递至今的天数。 */
    private Long daysSinceApplied;
}
