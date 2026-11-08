package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 错题本列表项。
 */
@Data
public class WrongQuestionVO implements Serializable {

    private Long id;

    private Long paperId;

    /** 岗位名称（错题归属的岗位） */
    private String jobTitle;

    private String questionType;

    private String difficulty;

    private String knowledgePoint;

    private String errorReason;

    private Integer timesWrong;

    private String status;

    private LocalDateTime createTime;
}