package com.chuan.module.exam.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 错题详情（含题目与正确答案解析）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WrongQuestionDetailVO extends WrongQuestionVO implements Serializable {

    private QuestionViewVO question;

    private String userAnswer;

    private String standardAnswer;

    private String analysis;

    private String extension;
}