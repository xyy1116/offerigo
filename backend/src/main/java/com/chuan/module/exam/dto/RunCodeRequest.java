package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 编程题在线运行请求。
 */
@Data
public class RunCodeRequest implements Serializable {

    private Long paperId;

    private Integer index;

    /** java / javascript */
    private String language;

    private String code;

    /** test-按测试用例运行 / debug-使用自定义输入调试 */
    private String mode;

    /** debug 模式的自定义标准输入 */
    private String stdin;
}