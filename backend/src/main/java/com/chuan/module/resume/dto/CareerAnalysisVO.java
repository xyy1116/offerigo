package com.chuan.module.resume.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 职业发展分析结果。
 */
@Data
public class CareerAnalysisVO implements Serializable {

    /** 与目标岗位的匹配度评分（0-100） */
    private Integer fitScore;

    /** 总体评价 */
    private String overview;

    /** 按优先级排序的发展建议 */
    private List<Advice> recommendations = new ArrayList<>();

    @Data
    public static class Advice implements Serializable {
        /** 优先级，从 1 开始递增 */
        private Integer priority;
        /** 分类：技能提升 / 经验积累 / 资质提升 */
        private String category;
        /** 建议标题 */
        private String title;
        /** 具体可执行的建议 */
        private String detail;
    }
}