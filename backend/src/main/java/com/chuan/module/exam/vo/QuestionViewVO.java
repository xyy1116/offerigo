package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 下发到前端的题目（剥离标准答案与解析，防止作答前泄题）。
 */
@Data
public class QuestionViewVO implements Serializable {

    private String questionId;

    private String type;

    private String stem;

    private List<String> options;

    private String difficulty;

    private String knowledgePoint;

    private List<String> testCases;

    private List<CodeTestCaseVO> codeTestCases;

    /** real-大厂真题 / ai-AI 生成 */
    private String sourceType;

    private String sourceCompany;

    private String sourceName;

    private String sourceUrl;

    private Integer sourceFrequency;
}