package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 链接导入岗位的请求体：用户粘贴的岗位详情页 URL。
 */
@Data
public class JobLinkRequest {

    private String url;
}