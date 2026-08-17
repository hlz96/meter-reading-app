package com.meter.app.auth;

import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 取当前登录用户。service 层用它拿 orgId 做数据隔离(TRD 5.4)。
 */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal;
    }

    public static Long orgId() {
        return get().orgId();
    }

    public static Long userId() {
        return get().userId();
    }

    public static String role() {
        return get().role();
    }
}
