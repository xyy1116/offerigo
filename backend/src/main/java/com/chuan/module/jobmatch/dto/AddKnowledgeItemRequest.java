package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 向已有入库批次中新增单条素材记录请求体。
 */
@Data
public class AddKnowledgeItemRequest {

    @NotBlank(message = "素材类型不能为空")
    @Size(max = 32, message = "类型长度不超过 32")
    private String type;

    @NotBlank(message = "素材内容不能为空")
    @Size(max = 5000, message = "内容长度不超过 5000 字符")
    private String content;

    /** 所属入库批次 ID，为空则作为独立单条记录。 */
    private String batchId;

    /** 可选入库时间（yyyy-MM-dd HH:mm），为空则使用服务器当前时间。 */
    private String createTime;
}
