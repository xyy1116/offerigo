package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 快速补充：基于岗位待补足项生成简历化素材草稿请求体。
 */
@Data
public class GenerateMaterialRequest {

    @NotBlank(message = "岗位要求不能为空")
    @Size(max = 2000, message = "岗位要求不超过 2000 字符")
    private String requirement;
}
