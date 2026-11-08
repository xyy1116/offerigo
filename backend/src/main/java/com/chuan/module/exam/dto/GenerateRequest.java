package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 生成笔试题目请求。
 * 优先使用 sourceRecordId（复用匹配分析结果中的 JD），否则使用 jobTitle + jdText 手动指定。
 */
@Data
public class GenerateRequest implements Serializable {

    /** paperType: practice-专项 / mock-限时模拟 */
    private String paperType;

    private Integer count;

    /** 难度：basic/middle/advanced（可选，限定难度） */
    private String difficulty;

    /** 专项选题维度：knowledge/type/difficulty（可选） */
    private String mode;

    /** 专项限定知识点（mode=knowledge 时使用） */
    private String knowledgePoint;

    /** 关联的匹配记录 ID（一键跳转来源） */
    private Long sourceRecordId;

    /** 手动指定的岗位名称 */
    private String jobTitle;

    /** 手动粘贴的岗位 JD 文本 */
    private String jdText;

    /** 限时模拟时长（秒） */
    private Integer durationSeconds;

    /** 前端生成的进度请求 ID，用于轮询出卷阶段 */
    private Long requestId;
}