package com.meter.app.auth.dto;

/** 验证码响应。骨架阶段返回 code 便于前端/测试;生产接短信后应移除 code 字段。 */
public record SmsCodeResponse(
        String code,
        long expiresInSeconds
) {
}
