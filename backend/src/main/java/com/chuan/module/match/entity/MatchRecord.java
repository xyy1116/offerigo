package com.chuan.module.match.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历与岗位 JD 匹配记录实体。
 */
@Data
@TableName("match_record")
public class MatchRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String jobTitle;

    private String resumeFilename;

    private BigDecimal overallScore;

    /** 完整分析结果 JSON（维度、高亮、原文） */
    private String resultJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}