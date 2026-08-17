package com.meter.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 绑定 application.yml 的 app.sms.* 配置。
 * enabled=false 时注册流程放开验证码校验(骨架/测试默认),避免强依赖短信网关。
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        boolean enabled,
        int codeLength,
        long ttlSeconds
) {
    public SmsProperties {
        if (codeLength <= 0) codeLength = 6;
        if (ttlSeconds <= 0) ttlSeconds = 300;
    }
}
