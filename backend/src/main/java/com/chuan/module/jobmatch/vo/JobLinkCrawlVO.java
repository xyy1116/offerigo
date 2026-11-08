package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 岗位链接导入抓取结果：
 * 成功时携带结构化岗位信息；失败时携带分类错误类型与部分识别原文，供前端一键转入粘贴导入。
 */
@Data
public class JobLinkCrawlVO implements Serializable {

    /** 是否抓取并标准化成功。 */
    private boolean success;

    /**
     * 失败分类：ANTI_BOT（动态渲染/反爬限制）、LINK_INVALID（链接无效/无法访问）、
     * CONTENT_EMPTY（未识别到有效岗位内容）。成功时为 null。
     */
    private String errorType;

    /** 失败时的友好提示文案。 */
    private String message;

    /** 部分识别出的原文（失败降级时带入粘贴导入框，避免重复输入）。 */
    private String partialText;

    /** 成功时的结构化岗位预览结果。 */
    private JobImportVO jobImport;
}