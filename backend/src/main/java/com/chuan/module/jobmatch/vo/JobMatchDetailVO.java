package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 岗位要求与简历内容的一一对应匹配详情条目。
 */
@Data
public class JobMatchDetailVO implements Serializable {

    /** 维度分组：技能 / 学历 / 项目 / 证书 / 其他。 */
    private String dimension;

    /** 岗位明确要求条目。 */
    private String requirement;

    /** 简历中与该要求对应的内容（未匹配时可为空）。 */
    private String resumeContent;

    /** 是否匹配。 */
    private Boolean matched;

    /** 匹配说明（匹配时，如「完全匹配」「相关匹配」）。 */
    private String matchType;

    /** 缺失说明（未匹配时，如「未掌握」「学历不满足」「缺少相关证书」）。 */
    private String missReason;

    /** 该待补足项补齐后可获得的确定性加分（预计加分 = 实际加分，全部待补足项加分总和 + 当前分数 = 100）。 */
    private Integer quota;
}