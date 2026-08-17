package com.meter.app.org.dto;

import jakarta.validation.constraints.NotBlank;

/** 生成邀请码请求:指定加入后的角色。 */
public record InviteRequest(
        @NotBlank String role
) {
}
