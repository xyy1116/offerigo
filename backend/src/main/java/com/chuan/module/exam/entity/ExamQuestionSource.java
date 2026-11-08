package com.chuan.module.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题库抓取来源状态，用内容 hash 跳过未变化的源。
 */
@Data
@TableName("exam_question_source")
public class ExamQuestionSource implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceKey;

    private String sourceName;

    private String sourceUrl;

    private String contentHash;

    private Integer questionCount;

    private String lastStatus;

    private LocalDateTime lastCrawlTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}