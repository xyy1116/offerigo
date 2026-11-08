package com.chuan.module.interview.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * AI 面试评估报告（企业级四维度结构化报告）。
 */
@Data
public class InterviewReportVO implements Serializable {

    /** 综合得分（0-100） */
    private Integer score;

    /** 评级：优秀 / 良好 / 中等 / 待提升 */
    private String level;

    /** 面试岗位 */
    private String position;

    /** 面试时长（分钟） */
    private Integer durationMinutes;

    /** 面试总问题数 */
    private Integer questionCount;

    /** 总体评价 */
    private String summary;

    /** 核心优势（2-3 条） */
    private List<String> strengths;

    /** 待提升点（2-3 条） */
    private List<String> weaknesses;

    /** 各维度评分明细（固定 4 项，每项满分 25） */
    private List<DimensionScore> dimensions;
}