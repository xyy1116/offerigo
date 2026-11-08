package com.chuan.module.interview.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 面试评分维度明细（四维度固定评分制，每项满分 25）。
 */
@Data
public class DimensionScore implements Serializable {

    /** 维度名称：专业能力深度 / 岗位匹配度 / 问题解决能力 / 表达逻辑 */
    private String name;

    /** 该维度得分（0-25） */
    private Integer score;

    /** 面试官对该维度的一句话评语 */
    private String comment;

    /** 评分证据：引用候选人原话或具体表现；为空的维度会被后端强制降档 */
    private String evidence;
}