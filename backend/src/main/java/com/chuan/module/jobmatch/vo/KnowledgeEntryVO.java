package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的简历素材条目视图。
 */
@Data
public class KnowledgeEntryVO implements Serializable {

    private Long id;

    private String type;

    private String content;

    private String batchId;

    private LocalDateTime createTime;
}