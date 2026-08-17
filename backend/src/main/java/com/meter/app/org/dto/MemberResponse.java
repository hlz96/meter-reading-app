package com.meter.app.org.dto;

import com.meter.app.org.entity.Member;

public record MemberResponse(
        Long id,
        Long userId,
        String role,
        Integer status
) {
    public static MemberResponse from(Member m) {
        return new MemberResponse(m.getId(), m.getUserId(), m.getRole().name(), m.getStatus());
    }
}
