package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 单题作答项。
 */
@Data
public class AnswerItem implements Serializable {

    /** 试题序号（从 0 开始） */
    private Integer index;

    private String answer;

    /** 编程题语言：java / javascript */
    private String language;
}