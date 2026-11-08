package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 针对单个目标岗位生成的定制简历结果。
 */
@Data
public class JobResumeVO implements Serializable {

    /** 岗位名称（回显）。 */
    private String jobName;

    /** 求职者姓名（用于 PDF 文件名）。 */
    private String name;

    /** 证件照（data URI，如 data:image/jpeg;base64,...，本地处理后仅导出时随请求提交）。 */
    private String photo;

    /** 岗位类型（用于前端选择差异化模板）：技术研发类/产品类/运营类/设计类/市场销售类/职能类。 */
    private String jobCategory;

    /** 个人基本信息概述。 */
    private String basicInfo;

    /** 自我评价：2-3 句精炼概括个人核心竞争力与求职意向。 */
    private String selfEvaluation;

    /** 核心技能（含是否匹配岗位的标记）。 */
    private List<ResumeSkillVO> skills;

    /** 实习/项目经历条目。 */
    private List<String> experiences;

    /** 与岗位最相关的项目/工作亮点（每条突出职责与可量化成果）。 */
    private List<String> projectHighlights;

    /** 教育背景条目。 */
    private List<String> education;

    /** 证书奖项条目。 */
    private List<String> certificates;

    /** 岗位要求但用户当前缺失的技术栈及学习建议。 */
    private List<MissingSkillVO> missingSkills;
}