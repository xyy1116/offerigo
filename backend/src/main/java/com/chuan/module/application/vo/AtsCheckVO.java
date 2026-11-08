package com.chuan.module.application.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * ATS（Applicant Tracking System）合规检查结果：验证简历 PDF 文本层是否可解析、关键联系方式与岗位关键词是否存在。
 */
@Data
public class AtsCheckVO implements Serializable {

    /** 简历 PDF 是否可提取有效文本层。 */
    private boolean hasText;

    /** 是否检测到手机号。 */
    private boolean hasPhone;

    /** 是否检测到邮箱。 */
    private boolean hasEmail;

    /** 提取到的联系方式片段（预览用）。 */
    private String contactLine;

    /** 检查的岗位关键词总数。 */
    private int keywordTotal;

    /** 命中的岗位关键词数。 */
    private int keywordHits;

    /** 命中的岗位关键词。 */
    private List<String> matchedKeywords;

    /** 未命中的岗位关键词。 */
    private List<String> missingKeywords;

    /** 改善建议。 */
    private List<String> suggestions;
}