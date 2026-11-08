package com.chuan.module.match.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 简历与 JD 匹配分析结果（由 DeepSeek 结构化输出）。
 */
@Data
public class MatchResultVO implements Serializable {

    /** 总体匹配度 0-100，保留一位小数 */
    private BigDecimal overallScore;

    /** 从 JD 中提取的岗位名称 */
    private String jobTitle;

    /** 总体评价 */
    private String summary;

    /** 技能维度得分列表 */
    private List<DimensionVO> dimensions;

    /** 简历中与 JD 匹配的关键点（用于绿色高亮） */
    private List<String> matchedPoints;

    /** JD 中简历缺失或不匹配的要求（用于红色高亮） */
    private List<String> missingRequirements;
}