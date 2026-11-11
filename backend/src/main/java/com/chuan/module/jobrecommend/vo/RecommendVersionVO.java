package com.chuan.module.jobrecommend.vo;

import lombok.Data;

import java.io.Serializable;

/** 今日岗位推荐版本：count>0 时 version 为当天日期序号，前端据此判断是否显示未读红点。 */
@Data
public class RecommendVersionVO implements Serializable {

    private String date;

    private Long version;

    private Integer count;

    /** 后端本次启动的标识；前端用于演示“每次启动项目亮一次 NEW”。 */
    private String runVersion;

    /** 今天已过 8 点但还没拿到今日数据（正在补爬），前端据此显示「今日岗位更新中」 */
    private Boolean pending;
}