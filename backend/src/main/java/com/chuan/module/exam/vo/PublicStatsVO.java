package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 公开统计：首页展示用，只暴露题库规模，不含用户数据。
 */
@Data
public class PublicStatsVO implements Serializable {

    /** 真题目库总题数。 */
    private Long questionCount;

    /** 题库覆盖的企业数量。 */
    private Integer companyCount;
}