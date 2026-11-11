package com.chuan.module.application.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 生成文书时引用的一条简历素材，用于前端展示「这段内容参考了哪些素材」。
 */
@Data
public class MaterialRefVO implements Serializable {

    /** 素材分类：项目经历 / 技能证书 / 教育经历等 */
    private String type;

    /** 素材内容摘要（截断展示） */
    private String content;
}