package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 待补足技术栈条目：岗位要求但用户当前未掌握的技能，附带简短学习优先级建议。
 */
@Data
public class MissingSkillVO implements Serializable {

    private String name;

    /** 学习优先级 / 简短建议。 */
    private String suggestion;
}