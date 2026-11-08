package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 单个岗位的匹配排名结果。
 */
@Data
public class JobRankVO implements Serializable {

    /** 岗位 ID（用于与岗位库记录一一对应，支撑生成简历/投简历）。 */
    private Long jobId;

    private String jobName;

    /** 匹配度等级：S / A / B / C / 不合格。 */
    private String level;

    /** 是否通过硬性门槛校验（true 合格 / false 不合格 / null 旧数据未判定）。 */
    private Boolean qualified;

    private BigDecimal score;

    /** 技能匹配子分（0-100）。 */
    private BigDecimal skillScore;

    /** 经历匹配子分（0-100）。 */
    private BigDecimal experienceScore;

    /** 学历与证书子分（0-100）。 */
    private BigDecimal educationScore;

    /** 岗位契合子分（0-100）。 */
    private BigDecimal fitScore;

    /** 未满足的硬性要求及扣分说明。 */
    private List<String> hardRequirementMisses;

    private List<String> coreRequirements;

    private List<String> techStack;

    /** 已匹配内容：用户知识库中与岗位要求高度契合的信息点（3-5 条）。 */
    private List<String> matched;

    /** 待补足内容：用户当前缺失但岗位明确要求的核心条件与技术栈（3-5 条）。 */
    private List<String> gaps;

    /** 岗位要求与简历内容的一一对应匹配详情（按技能/学历/项目/证书/其他维度分组）。 */
    private List<JobMatchDetailVO> details;

    private String summary;

    private String advantages;

    private String disadvantages;

    private String optimization;
}