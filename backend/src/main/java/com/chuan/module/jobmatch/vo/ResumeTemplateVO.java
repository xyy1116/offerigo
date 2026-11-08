package com.chuan.module.jobmatch.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;

/**
 * 简历模板：从外部网络模板实时爬取后提取出的设计特征（配色 + 字体），
 * 用于前端切换预览与 PDF/Word 导出一致渲染。
 */
@Data
public class ResumeTemplateVO implements Serializable {

    /** 模板唯一标识。 */
    private String id;

    /** 模板名称（如「经典蓝调」「商务紫调」）。 */
    private String name;

    /** 来源 URL（仅内部使用，不对外暴露）。 */
    @JsonIgnore
    private String source;

    /** 是否来自网络实时爬取（仅内部使用，不对外暴露）。 */
    @JsonIgnore
    private Boolean crawled;

    /** 主色（用于标题/分隔线/强调色）。 */
    private String primary;

    /** 辅色（用于次要强调/图标）。 */
    private String secondary;

    /** 页面/卡片背景色。 */
    private String background;

    /** 正文文字色。 */
    private String text;

    /** 标题字体栈。 */
    private String headingFont;

    /** 正文字体栈。 */
    private String bodyFont;

    /** 布局风格：single（单栏） / double（双栏）。 */
    private String layout;
}