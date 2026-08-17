package com.meter.app.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册并创建组织(管理员),对应 TRD 4.6。
 * 骨架阶段验证码校验先放开,后续接入短信服务。
 */
public record RegisterRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "手机号需11位数字")
        String phone,

        String smsCode,

        @NotBlank @Size(min = 8, max = 20, message = "密码需8-20位")
        String password,

        @NotBlank @Size(max = 100)
        String orgName
) {
}
