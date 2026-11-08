package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 题库版本：前端据此判断是否有"未看过的题库更新"。
 */
@Data
public class BankVersionVO implements Serializable {

    /** 最近一次题库抓取时间（ISO 字符串），题库为空时为空串。 */
    private String version;

    /** 题库总题数。 */
    private Long count;
}