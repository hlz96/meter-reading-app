package com.meter.app.auth.dto;

/**
 * 登录/注册成功返回,对应 TRD 4.6。
 */
public record AuthResponse(
        Long userId,
        Long orgId,
        String role,
        String accessToken,
        String refreshToken,
        long expiresIn
) {
}
