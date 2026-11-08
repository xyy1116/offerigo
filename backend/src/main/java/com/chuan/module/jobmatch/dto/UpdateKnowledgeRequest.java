package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 编辑单条素材记录请求体（修改属性分类、内容与可选入库时间）。
 */
@Data
public class UpdateKnowledgeRequest {

    private String type;

    private String content;

    /** 可选入库时间（yyyy-MM-dd HH:mm），为空则不改动入库时间。 */
    private String createTime;
}