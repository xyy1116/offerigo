package com.chuan.module.match.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史对比记录列表项。
 */
@Data
public class MatchRecordVO implements Serializable {

    private Long id;
    private String jobTitle;
    private String resumeFilename;
    private BigDecimal overallScore;
    private LocalDateTime createTime;
}