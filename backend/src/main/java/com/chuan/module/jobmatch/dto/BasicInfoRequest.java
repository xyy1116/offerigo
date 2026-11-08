package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 必填信息提交请求体（姓名、学历、毕业院校、专业）。
 */
@Data
public class BasicInfoRequest {

    private String name;

    private String education;

    private String school;

    private String major;
}