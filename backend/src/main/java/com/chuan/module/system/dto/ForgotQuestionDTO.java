package com.chuan.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 忘记密码第一步：查询密保问题请求参数。
 */
@Data
public class ForgotQuestionDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;
}