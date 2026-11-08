package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 试卷详情（下发作答用，题目不含答案）。
 */
@Data
public class ExamPaperDetailVO implements Serializable {

    private Long id;

    private String jobTitle;

    private String techStack;

    private String paperType;

    private String mode;

    private Integer durationSeconds;

    private String status;

    private Integer totalCount;

    private BigDecimal totalScore;

    private List<QuestionViewVO> questions;

    private ExamReportVO report;

    /** 本卷大厂真题数量 */
    private Integer realCount;

    /** 本卷 AI 生成题目数量 */
    private Integer aiCount;

    private Integer singleCount;

    private Integer multipleCount;

    private Integer shortAnswerCount;

    private Integer codingCount;

    /** 出卷时题库是否可用 */
    private Boolean bankReady;
}