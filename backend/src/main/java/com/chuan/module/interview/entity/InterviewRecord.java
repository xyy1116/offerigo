package com.chuan.module.interview.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 面试记录实体：保存一次完整面试的对话记录与评估报告。
 */
@Data
@TableName("interview_record")
public class InterviewRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 面试岗位 */
    private String position;

    /** 核心技术栈（注入面试提问） */
    private String techStack;

    /** 岗位 JD 原文（注入面试提问） */
    private String jdText;

    /** 题库方向：java / frontend / test / general */
    private String questionBankKey;

    /** 当前阶段：overview/tech/project/scenario/reverse，完成时为 null */
    private String stage;

    /** 当前阶段已问问题数 */
    private Integer stageQuestionCount;

    /** 面试总问题数 */
    private Integer questionCount;

    /** 状态：in_progress-进行中 / done-已完成 */
    private String status;

    /** 完整对话记录（纯文本） */
    private String transcript;

    /** 评估报告 JSON */
    private String reportJson;

    /** 综合得分 */
    private BigDecimal score;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}