package com.qivo.exception;

import lombok.Getter;
import com.qivo.result.ErrorCode;

/**
 * 业务异常 —— Service 层抛出，由全局异常处理器统一捕获
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.code = errorCode.getCode();
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BusinessException(String msg) {
        super(msg);
        this.code = ErrorCode.CONFLICT.getCode();
    }
}
