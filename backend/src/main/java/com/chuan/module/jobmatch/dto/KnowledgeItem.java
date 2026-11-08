package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 批量入库的素材记录项：属性分类 + 内容 + 可选的需要被替换的旧记录 ID。
 */
@Data
public class KnowledgeItem {

    private String type;

    private String content;

    /** 非空时表示「保留新记录」：先删除该旧记录再插入新记录。 */
    private Long replaceId;

    /** 可选入库时间（yyyy-MM-dd HH:mm），为空则使用服务器当前时间。 */
    private String createTime;
}