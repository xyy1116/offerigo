package com.chuan.module.jobmatch.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 投简历渠道匹配请求：用户确认公司名称后重新匹配双渠道。
 */
@Data
public class ApplyChannelRequest implements Serializable {

    /** 岗位 ID。 */
    private Long jobId;

    /** 招聘单位（补填时可空）。 */
    private String companyName;

    /** 招聘邮箱（补填时可空）。 */
    private String email;
}