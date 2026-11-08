package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 完整题目（含标准答案与解析，用于存储与批改）。
 */
@Data
public class QuestionVO implements Serializable {

    private String questionId;

    /** single/multiple/short_answer/coding */
    private String type;

    private String stem;

    private List<String> options;

    private String answer;

    private String analysis;

    /** basic/middle/advanced */
    private String difficulty;

    private String knowledgePoint;

    private List<String> testCases;

    /** 可执行测试用例（标准输入/期望输出），仅编程题使用 */
    private List<CodeTestCaseVO> codeTestCases;

    /** real-大厂真题 / ai-AI 生成 */
    private String sourceType;

    private String sourceCompany;

    private String sourceName;

    private String sourceUrl;

    /** 面经出现频次（真题才有） */
    private Integer sourceFrequency;
}