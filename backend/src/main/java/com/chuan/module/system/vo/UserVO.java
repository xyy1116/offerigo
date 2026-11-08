package com.chuan.module.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户安全信息（不包含密码）。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
}