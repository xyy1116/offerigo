package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 简历「核心技能」条目：matched 表示是否符合岗位要求（前端用于绿色高亮标注）。
 */
@Data
public class ResumeSkillVO implements Serializable {

    private String name;

    /** 是否匹配岗位要求的技术栈（true 前端绿色高亮）。 */
    private Boolean matched;
}