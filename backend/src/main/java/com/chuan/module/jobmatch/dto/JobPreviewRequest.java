package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 粘贴导入岗位的预览请求体：用户粘贴的招聘原文。
 */
@Data
public class JobPreviewRequest {

    private String content;
}
