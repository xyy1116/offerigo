package com.chuan.common.exception;

import com.chuan.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常，业务代码中主动抛出，由全局异常处理器统一转换为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.ERROR.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}