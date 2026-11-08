package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开统计：首页展示平台规模，只包含题库与来源信息，不含用户数据。
 */
@Data
public class PublicStatsVO implements Serializable {

    /** 真题目库总题数。 */
    private Long questionCount;

    /** 题库覆盖的企业数量。 */
    private Integer companyCount;

    /** 真题来源数量。 */
    private Integer sourceCount;

    /** 题库涉及的技术标签数量（去重）。 */
    private Integer tagCount;
}