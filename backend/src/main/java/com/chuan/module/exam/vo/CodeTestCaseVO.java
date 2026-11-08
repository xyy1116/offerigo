package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 可执行编程题测试用例：标准输入 + 期望标准输出。
 */
@Data
public class CodeTestCaseVO implements Serializable {

    private String input;

    private String expectedOutput;

    private String explanation;
}