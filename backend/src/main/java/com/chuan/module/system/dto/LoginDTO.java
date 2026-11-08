package com.chuan.module.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 图形验证码标识 */
    private String captchaId;

    /** 图形验证码输入 */
    private String captchaCode;

    /** 记住我：true 使用长有效期令牌 */
    private Boolean rememberMe;
}