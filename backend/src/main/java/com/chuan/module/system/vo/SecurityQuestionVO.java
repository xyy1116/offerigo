package com.chuan.module.system.vo;

import lombok.Data;

/**
 * 忘记密码查询密保问题返回信息。
 */
@Data
public class SecurityQuestionVO {

    private String username;
    private String securityQuestion;
}