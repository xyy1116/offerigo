package com.chuan.module.jobmatch.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的简历素材条目：单条素材记录，对应一个属性分类 + 一条内容。
 */
@Data
@TableName("knowledge_entry")
public class KnowledgeEntry implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 属性分类：基础信息/教育经历/项目经历/实习经历/技能证书/荣誉奖项/其他。 */
    private String type;

    private String content;

    /** 入库批次标识：一次入库操作生成的多条记录共享同一 batchId（用于卡片壳分组）。 */
    private String batchId;

    /** 拆分格式版本：2=当前「分类前缀：内容」格式；null/<2=旧格式，需重跑拆分。 */
    private Integer splitVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}