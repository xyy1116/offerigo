package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 试卷批改报告。
 */
@Data
public class ExamReportVO implements Serializable {

    private Long paperId;

    private String jobTitle;

    private String paperType;

    private BigDecimal totalScore;

    private BigDecimal userScore;

    private Integer durationSeconds;

    private Integer wrongCount;

    /** 知识点维度得分率（0-100），用于雷达图 */
    private Map<String, BigDecimal> dimensionScores;

    private List<GradingItemVO> items;

    private String summary;
}