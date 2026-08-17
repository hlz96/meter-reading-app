package com.meter.app.auth;

/**
 * 从 JWT 解析出的当前登录用户上下文,存入 SecurityContext。
 */
public record AuthPrincipal(Long userId, Long orgId, String role) {
}
