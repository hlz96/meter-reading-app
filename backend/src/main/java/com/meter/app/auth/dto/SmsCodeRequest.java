package com.meter.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 获取验证码请求。scene 可空,默认 REGISTER。 */
public record SmsCodeRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "手机号需11位数字")
        String phone,
        String scene
) {
}
