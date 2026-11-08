package com.chuan.module.jobmatch.vo;

import lombok.Data;

import java.util.List;

/**
 * 简历文档导入预览结果：原文 + AI 拆分后的记录 + 疑似重复提示。
 */
@Data
public class ResumeImportVO {

    private String fileName;

    private String originalText;

    private List<KnowledgeEntryVO> records;

    private List<DuplicateVO> duplicates;
}