package com.meter.app.org.dto;

import jakarta.validation.constraints.NotBlank;

/** 加入组织请求。 */
public record JoinRequest(
        @NotBlank String code
) {
}
