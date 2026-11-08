package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 投简历双渠道识别结果：公司名称、招聘邮箱、官网投递地址与各渠道可用状态。
 * 全程仅提供跳转/复制/唤起辅助，不记录投递状态、不存储账号信息。
 */
@Data
public class ApplyIntentVO implements Serializable {

    /** 岗位 ID。 */
    private Long jobId;

    /** 岗位名称（用于邮件主题与搜索关键词）。 */
    private String jobName;

    /** 识别出的企业/公司名称（未识别到或存在多个候选人时为 null）。 */
    private String companyName;

    /** 多个候选公司名称（同名/歧义时由用户确认选择）。 */
    private List<String> companyCandidates;

    /** 招聘邮箱（岗位文本提取或企业搜索所得，未查到为 null）。 */
    private String email;

    /** 邮箱来源：job_text（岗位文本）/ search（企业搜索）。 */
    private String emailSource;

    /** 官网投递地址：命中模板库直达官方招聘门户；未命中则为 null。 */
    private String portalUrl;

    /** 搜索引擎兜底地址：未命中模板库时使用「公司+岗位+招聘投递」搜索。 */
    private String searchUrl;

    /** 是否已有该岗位的原始详情链接（推荐来源/链接导入/历史推荐回填），有则可直接跳转。 */
    private Boolean jobLinkAvailable;

    /** 已保存的岗位原始详情链接，前端可直接打开，无需再等待检索。 */
    private String jobLink;

    /** 官网投递渠道是否可用。 */
    private Boolean portalAvailable;

    /** 官网投递不可用原因。 */
    private String portalUnavailableReason;

    /** 邮箱投递渠道是否可用。 */
    private Boolean emailAvailable;

    /** 邮箱投递不可用原因。 */
    private String emailUnavailableReason;
}