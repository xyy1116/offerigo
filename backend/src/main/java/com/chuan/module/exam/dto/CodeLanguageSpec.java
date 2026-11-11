package com.chuan.module.exam.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一种可运行语言的配置：写哪个文件、怎么编译、怎么运行。
 * 配置见 resources/exam/languages.json，新增语言只需加一段配置并安装对应工具链。
 */
@Data
public class CodeLanguageSpec implements Serializable {

    /** 展示名，如 Python 3 */
    private String label;

    /** 危险调用规则族：java / javascript / python / c / generic */
    private String family = "generic";

    /** 源码文件名，如 main.py */
    private String file;

    /** 编译命令，可用 {file} 与 {dir} 占位；解释型语言留空 */
    private List<String> compile = new ArrayList<>();

    /** 运行命令，可用 {file} 与 {dir} 占位 */
    private List<String> run = new ArrayList<>();
}