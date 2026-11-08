package com.chuan.module.match.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 返回给前端的完整分析结果（含原文，用于并列展示与高亮）。
 */
@Data
public class MatchAnalyzeVO implements Serializable {

    private Long recordId;

    private BigDecimal overallScore;

    private String jobTitle;

    private String summary;

    private List<DimensionVO> dimensions;

    private List<String> matchedPoints;

    private List<String> missingRequirements;

    /** 简历原文 */
    private String resumeText;

    /** 岗位 JD 原文 */
    private String jdText;
}