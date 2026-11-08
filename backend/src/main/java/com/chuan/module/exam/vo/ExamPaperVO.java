package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷列表项。
 */
@Data
public class ExamPaperVO implements Serializable {

    private Long id;

    private String jobTitle;

    private String paperType;

    private Integer totalCount;

    private BigDecimal totalScore;

    private BigDecimal userScore;

    private String status;

    private LocalDateTime createTime;
}