package com.chuan.module.jobrecommend.task;

import lombok.Data;

/** 爬虫候选岗位：各来源统一结构，入库前只做去重与向量化。 */
@Data
public class CrawlCandidate {

    private String jobName;
    private String companyName;
    private String location;
    private String salary;
    private String requirement;
    private String sourceUrl;
    private String embedText;
}