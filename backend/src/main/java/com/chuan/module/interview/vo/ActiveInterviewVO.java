package com.chuan.module.interview.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 进行中面试概要：供前端判断是否弹「继续上次面试」入口。
 */
@Data
public class ActiveInterviewVO implements Serializable {

    private Long id;

    /** 面试岗位 */
    private String position;

    /** 当前阶段 key */
    private String stage;

    /** 已问问题数 */
    private Integer questionCount;

    private LocalDateTime updateTime;
}