package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 添加目标岗位的请求体。
 */
@Data
public class AddJobRequest {

    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 120, message = "岗位名称不超过 120 字符")
    private String jobName;

    @Size(max = 8000, message = "岗位要求不超过 8000 字符")
    private String jobRequirement;

    @Size(max = 120, message = "公司名称不超过 120 字符")
    private String companyName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 120, message = "邮箱不超过 120 字符")
    private String email;

    /** 工作地点。 */
    @Size(max = 120, message = "地点不超过 120 字符")
    private String location;

    /** 薪资范围。 */
    @Size(max = 80, message = "薪资不超过 80 字符")
    private String salary;

    @Size(max = 500, message = "技术栈不超过 500 字符")
    private String techStack;

    @Size(max = 64, message = "岗位类型不超过 64 字符")
    private String jobType;

    /** 岗位原始详情链接（链接导入时带回，选填）。 */
    @Size(max = 500, message = "岗位链接不超过 500 字符")
    private String jobLink;

    /** 来源平台（推荐入库时写入，链接导入可由后端按域名推断）。 */
    @Size(max = 100, message = "来源平台不超过 100 字符")
    private String sourcePlatform;
}
