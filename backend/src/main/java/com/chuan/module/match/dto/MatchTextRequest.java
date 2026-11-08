package com.chuan.module.match.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 基于在线简历文字内容的匹配分析请求（模块二 → 模块一 联动）。
 */
@Data
public class MatchTextRequest implements Serializable {

    /** 简历纯文本内容 */
    private String resumeText;

    /** JD 输入方式：text */
    private String jdType;

    /** 岗位 JD 文字内容 */
    private String jdText;
}