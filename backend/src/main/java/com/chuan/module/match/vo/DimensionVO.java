package com.chuan.module.match.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 单个技能维度得分。
 */
@Data
public class DimensionVO implements Serializable {

    /** 维度名称，如「Java 技术栈」 */
    private String name;

    /** 匹配得分 0-100 */
    private BigDecimal score;

    /** 简短评述 */
    private String remark;
}