package com.chuan.module.interview.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 面试记录视图对象。
 */
@Data
public class InterviewRecordVO implements Serializable {

    private Long id;
    private String position;
    private BigDecimal score;
    private InterviewReportVO report;
    private LocalDateTime createTime;
    private String transcript;
}