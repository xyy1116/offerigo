package com.chuan.module.resume.dto;

import lombok.Data;

/**
 * AI 润色结果：原始内容与润色后内容。
 */
@Data
public class PolishVO {

    private String original;

    private String polished;
}