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
 * 用户真题使用记录，用于优先出没练过的题。
 */
@Data
@TableName("exam_question_usage")
public class ExamQuestionUsage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String questionHash;

    private LocalDateTime lastUsedTime;

    private Integer usedCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}