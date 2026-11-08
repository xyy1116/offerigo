package com.chuan.module.jobrecommend.vo;

import lombok.Data;

import java.io.Serializable;

/** 今日岗位推荐版本：count>0 时 version 为当天日期序号，前端据此判断是否显示未读红点。 */
@Data
public class RecommendVersionVO implements Serializable {

    private String date;

    private Long version;

    private Integer count;
}