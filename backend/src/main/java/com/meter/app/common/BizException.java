package com.meter.app.common;

import lombok.Getter;

/**
 * 业务异常,携带 TRD 7.1 的错误码。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
