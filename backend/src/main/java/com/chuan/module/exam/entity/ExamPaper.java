package com.chuan.module.exam.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 笔试试卷记录实体。
 */
@Data
@TableName("exam_paper")
public class ExamPaper implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String jobTitle;

    private String techStack;

    private String paperType;

    private String mode;

    private Long sourceRecordId;

    private Integer totalCount;

    private BigDecimal totalScore;

    private BigDecimal userScore;

    private String status;

    private Integer durationSeconds;

    private String questionsJson;

    private String answersJson;

    private String reportJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}