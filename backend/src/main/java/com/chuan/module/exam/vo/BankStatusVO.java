package com.chuan.module.exam.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 大厂真题目库状态，用于前端展示题库规模与更新情况。
 */
@Data
public class BankStatusVO implements Serializable {

    private Integer total;

    private Integer companyCount;

    private Integer sourceCount;

    private LocalDateTime lastUpdate;

    private Boolean crawling;

    private List<SourceItem> sources;

    @Data
    public static class SourceItem implements Serializable {
        private String sourceName;
        private String sourceUrl;
        private Integer questionCount;
        private String lastStatus;
        private LocalDateTime lastCrawlTime;
    }
}