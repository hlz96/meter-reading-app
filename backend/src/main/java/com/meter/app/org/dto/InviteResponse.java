package com.meter.app.org.dto;

import com.meter.app.org.entity.Invitation;

/** 邀请码响应。 */
public record InviteResponse(
        String code,
        String role,
        long expiresInSeconds
) {
    public static InviteResponse from(Invitation inv, long expiresInSeconds) {
        return new InviteResponse(inv.getCode(), inv.getRole().name(), expiresInSeconds);
    }
}
