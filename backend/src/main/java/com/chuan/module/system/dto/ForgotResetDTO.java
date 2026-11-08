package com.chuan.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 忘记密码第二步：验证密保答案并重置密码请求参数。
 */
@Data
public class ForgotResetDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密保答案不能为空")
    private String securityAnswer;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在6-32之间")
    private String newPassword;
}