package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 编程题运行结果。
 */
@Data
public class CodeRunResultVO implements Serializable {

    private Boolean supported;

    /** 不支持运行时的说明 */
    private String message;

    private Boolean compiled;

    private String compileError;

    private Integer passed;

    private Integer total;

    private Long totalMs;

    private List<CodeCaseResultVO> cases;

    @Data
    public static class CodeCaseResultVO implements Serializable {
        private Integer index;
        private String input;
        private String expected;
        private String actual;
        private Boolean passed;
        private Boolean timedOut;
        private String error;
        private Long runtimeMs;
    }
}