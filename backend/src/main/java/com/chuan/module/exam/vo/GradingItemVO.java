package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单题批改结果。
 */
@Data
public class GradingItemVO implements Serializable {

    private Integer index;

    private String questionId;

    private String type;

    private String stem;

    private String userAnswer;

    private BigDecimal score;

    private BigDecimal fullScore;

    private Boolean correct;

    private String standardAnswer;

    private String analysis;

    private String errorReason;

    private String extension;
}