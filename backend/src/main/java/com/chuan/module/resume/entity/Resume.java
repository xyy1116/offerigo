package com.chuan.module.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线简历实体：以 JSON 形式存储完整简历内容。
 */
@Data
@TableName("resume")
public class Resume implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 完整简历内容 JSON */
    private String contentJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}