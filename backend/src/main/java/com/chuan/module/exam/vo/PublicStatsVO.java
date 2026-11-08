package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开统计：首页展示平台规模，覆盖题库、岗位、素材与求职记录，不含用户隐私数据。
 */
@Data
public class PublicStatsVO implements Serializable {

    /** 真题目库总题数。 */
    private Long questionCount;

    /** 招聘岗位数量（推荐池 + 目标岗位）。 */
    private Long jobCount;

    /** 简历素材条目数。 */
    private Long materialCount;

    /** AI 求职记录数（模拟面试 + 笔试试卷 + 投递）。 */
    private Long activityCount;
}