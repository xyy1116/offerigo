package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 快速补充关闭待补足项请求体：新增一条素材并确定性补分。
 */
@Data
public class CloseGapRequest {

    @NotNull(message = "岗位 ID 不能为空")
    private Long jobId;

    @NotBlank(message = "待补足项内容不能为空")
    @Size(max = 500, message = "待补足项内容长度不超过 500")
    private String requirement;

    @NotBlank(message = "素材类型不能为空")
    @Size(max = 32, message = "类型长度不超过 32")
    private String type;

    @NotBlank(message = "素材内容不能为空")
    @Size(max = 5000, message = "内容长度不超过 5000 字符")
    private String content;
}