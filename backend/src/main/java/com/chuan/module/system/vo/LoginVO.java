package com.chuan.module.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录返回信息。
 */
@Data
@Builder
public class LoginVO {

    private String token;
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
}