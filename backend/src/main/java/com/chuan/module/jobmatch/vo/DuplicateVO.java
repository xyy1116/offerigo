package com.chuan.module.jobmatch.vo;

import lombok.Data;

/**
 * 疑似重复提示：新增记录下标 + 与之疑似重复的已有记录。
 */
@Data
public class DuplicateVO {

    /** 本次拆分结果 records 中的下标（从 0 开始）。 */
    private int recordIndex;

    /** 与之疑似重复的已有记录。 */
    private KnowledgeEntryVO existing;

    /** 整体文本重合度百分比（0-100），用于重复提示中展示。 */
    private Integer similarity;
}