package com.chuan.module.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度需在3-20之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在6-32之间")
    private String password;

    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "请设置密保问题")
    private String securityQuestion;

    @NotBlank(message = "请设置密保答案")
    private String securityAnswer;

    /** 图形验证码标识 */
    private String captchaId;

    /** 图形验证码输入 */
    private String captchaCode;
}