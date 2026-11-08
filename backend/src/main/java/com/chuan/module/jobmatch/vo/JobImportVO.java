package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 目标岗位导入预览视图：原文 + AI 提取整理后的「岗位名称 + 岗位要求」结构化结果。
 */
@Data
public class JobImportVO implements Serializable {

    private String fileName;

    /** 文档解析出的原始文本（粘贴导入时为粘贴内容）。 */
    private String originalText;

    private String jobName;

    private String jobRequirement;

    /** 招聘单位（智能识别原文中的公司名称）。 */
    private String companyName;

    /** 招聘邮箱（智能识别原文中的邮箱）。 */
    private String email;

    /** 工作地点（智能识别）。 */
    private String location;

    /** 薪资范围（智能识别）。 */
    private String salary;

    private String techStack;

    /** AI 识别出的岗位类型标签。 */
    private String jobType;
}
