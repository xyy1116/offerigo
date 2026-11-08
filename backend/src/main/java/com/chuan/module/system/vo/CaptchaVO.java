package com.chuan.module.system.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 图形验证码返回信息。
 */
@Data
@Builder
public class CaptchaVO {

    /** 验证码标识，校验时回传 */
    private String captchaId;

    /** base64 编码的 PNG 图片（data:image/png;base64,...） */
    private String image;
}