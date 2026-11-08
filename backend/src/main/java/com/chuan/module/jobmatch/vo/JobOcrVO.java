package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 目标岗位 JD 截图 OCR 识别结果：识别全文 + 提取并合并后的核心技术栈。
 */
@Data
public class JobOcrVO implements Serializable {

    private Long jobId;

    private String jobName;

    /** 识别出的完整 JD 文字内容。 */
    private String ocrText;

    /** 提取并合并后的核心技术栈（前端高亮展示）。 */
    private String techStack;
}