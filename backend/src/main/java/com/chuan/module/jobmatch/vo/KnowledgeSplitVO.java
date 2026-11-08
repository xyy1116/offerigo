package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.util.List;

/**
 * 素材拆分预览结果：拆分后的记录 + 疑似重复提示。
 */
@Data
public class KnowledgeSplitVO {

    private List<KnowledgeEntryVO> records;

    private List<DuplicateVO> duplicates;
}