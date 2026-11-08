package com.chuan.module.resume.dto;

import lombok.Data;

/**
 * AI 润色请求。
 */
@Data
public class PolishRequest {

    /** 待润色的原文 */
    private String text;

    /** 所属板块，例如：个人简介 / 工作经历 / 项目经验 / 技能特长 */
    private String section;
}