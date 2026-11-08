package com.chuan.module.jobmatch.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量入库请求体。
 */
@Data
public class SaveKnowledgeRequest {

    private List<KnowledgeItem> items;
}