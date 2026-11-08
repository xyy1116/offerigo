package com.chuan.module.application.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 针对目标岗位生成的中文求职信。
 */
@Data
public class CoverLetterVO implements Serializable {

    /** 求职信主题，如「应聘 Java 开发工程师 求职信」。 */
    private String subject;

    /** 称呼，如「尊敬的招聘负责人：」。 */
    private String greeting;

    /** 求职信正文（分段，换行分隔）。 */
    private String body;

    /** 落款姓名（取自简历基本信息）。 */
    private String applicantName;
}