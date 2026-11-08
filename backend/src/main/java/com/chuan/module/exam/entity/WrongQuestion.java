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
 * 错题本实体。
 */
@Data
@TableName("wrong_question")
public class WrongQuestion implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long paperId;

    /** 岗位名称（关联试卷的岗位，用于按岗位归类） */
    private String jobTitle;

    private String questionId;

    private String questionType;

    private String difficulty;

    private String knowledgePoint;

    private String questionJson;

    private String userAnswer;

    private String standardAnswer;

    private String analysis;

    private String errorReason;

    private String extension;

    private Integer timesWrong;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}