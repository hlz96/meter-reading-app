package com.meter.app.auth;

import com.meter.app.auth.dto.AuthResponse;
import com.meter.app.auth.dto.LoginRequest;
import com.meter.app.auth.dto.RegisterRequest;
import com.meter.app.auth.dto.SmsCodeRequest;
import com.meter.app.auth.dto.SmsCodeResponse;
import com.meter.app.common.ApiResponse;
import com.meter.app.config.SmsProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SmsCodeService smsCodeService;
    private final SmsProperties smsProperties;

    public AuthController(AuthService authService,
                          SmsCodeService smsCodeService,
                          SmsProperties smsProperties) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
        this.smsProperties = smsProperties;
    }

    /** 获取验证码(注册前调用)。骨架阶段响应回传验证码,便于联调/测试。 */
    @PostMapping("/sms-code")
    public ApiResponse<SmsCodeResponse> smsCode(@Valid @RequestBody SmsCodeRequest req) {
        String code = smsCodeService.generate(req.phone(), req.scene());
        return ApiResponse.ok(new SmsCodeResponse(code, smsProperties.ttlSeconds()));
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@RequestBody RefreshRequest req) {
        return ApiResponse.ok(authService.refresh(req.refreshToken()));
    }

    /** 受保护:返回当前登录用户,用于验证 JWT 鉴权。 */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(Map.of(
                "userId", principal.userId(),
                "orgId", principal.orgId(),
                "role", principal.role()
        ));
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }
}
