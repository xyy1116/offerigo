package com.chuan.module.jobmatch.dto;

import lombok.Data;

/**
 * 编辑目标岗位的请求体：仅允许修改岗位名称与岗位要求，入库时间保持不变。
 */
@Data
public class UpdateJobRequest {

    private String jobName;

    private String jobRequirement;

    private String companyName;

    private String email;

    /** 工作地点。 */
    private String location;

    /** 薪资范围。 */
    private String salary;
}
