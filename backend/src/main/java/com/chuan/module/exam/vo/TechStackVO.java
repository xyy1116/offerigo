package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 岗位技术栈提取结果。
 */
@Data
public class TechStackVO implements Serializable {

    private String jobTitle;

    /** 岗位分类：technical-计算机技术岗 / non-technical-非技术岗 */
    private String category;

    private List<String> techStacks;

    /** 是否属于 AI 笔试助手支持的 IT 行业岗位 */
    private Boolean itSupported;

    /** 该岗位是否需要编程能力；决定是否自动出编程题 */
    private Boolean codingRequired;

    /** 不支持时给用户看的原因 */
    private String unsupportedReason;
}