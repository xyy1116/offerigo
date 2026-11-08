package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改投递状态与备注。
 */
@Data
public class UpdateApplicationRequest implements Serializable {

    @Size(max = 20, message = "投递状态不超过 20 字符")
    private String status;

    @Size(max = 1000, message = "备注不超过 1000 字符")
    private String remark;
}
