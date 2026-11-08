package com.chuan.module.jobrecommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 爬虫岗位：每天定时抓取的外部岗位，job_vector 为岗位描述+要求文本的向量 JSON。
 */
@Data
@TableName("crawl_job")
public class CrawlJob implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String jobName;

    private String companyName;

    private String location;

    private String salary;

    private String jobDesc;

    private String requirement;

    private String sourceUrl;

    private LocalDate crawlDate;

    private LocalDateTime crawlTime;

    private String jobVector;
}