package com.meter.app.common;

/**
 * 错误码常量,对应 TRD 7.1。
 */
public final class ErrorCode {

    private ErrorCode() {}

    public static final int PARAM_INVALID = 1001;
    public static final int SMS_CODE_INVALID = 1002;
    public static final int UNAUTHORIZED = 2001;
    public static final int TOKEN_EXPIRED = 2002;
    public static final int FORBIDDEN = 2003;
    public static final int CROSS_ORG = 2004;
    public static final int NOT_FOUND = 3001;
    public static final int CONFLICT = 3002;
    public static final int INVITATION_INVALID = 3003;
    public static final int IMPORT_PARSE_FAIL = 4001;
    public static final int READING_ABNORMAL = 4002;
    public static final int PERIOD_NOT_SETTLEABLE = 4003;
    public static final int SERVER_ERROR = 5000;
}
