package com.chuan.module.jobmatch.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 确认投递成功：草稿转正式记录时可修正简历版本。
 */
@Data
public class ConfirmApplicationRequest implements Serializable {

    @Size(max = 200, message = "简历版本不超过 200 字符")
    private String resumeVersion;
}
