package com.meter.app.org.dto;

import com.meter.app.org.entity.Member;

/**
 * 加入组织响应。只返回新组织与角色;用户需用该组织身份重新登录以获取新 token(切组)。
 */
public record JoinResponse(
        Long orgId,
        String role
) {
    public static JoinResponse from(Member m) {
        return new JoinResponse(m.getOrgId(), m.getRole().name());
    }
}
