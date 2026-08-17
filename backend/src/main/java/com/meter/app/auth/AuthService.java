package com.meter.app.auth;

import com.meter.app.auth.dto.AuthResponse;
import com.meter.app.auth.dto.LoginRequest;
import com.meter.app.auth.dto.RegisterRequest;
import com.meter.app.auth.entity.User;
import com.meter.app.auth.repository.UserRepository;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Organization;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import com.meter.app.org.repository.OrganizationRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository orgRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SmsCodeService smsCodeService;
    private final com.meter.app.config.SmsProperties smsProperties;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository orgRepository,
                       MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       SmsCodeService smsCodeService,
                       com.meter.app.config.SmsProperties smsProperties) {
        this.userRepository = userRepository;
        this.orgRepository = orgRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.smsCodeService = smsCodeService;
        this.smsProperties = smsProperties;
    }

    /**
     * 注册:创建用户 + 组织 + 管理员成员。三者同一事务。
     */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // 验证码校验(开关默认关闭,骨架/测试放开;开启后强校验)
        if (smsProperties.enabled()) {
            smsCodeService.verifyAndConsume(req.phone(), SmsCodeService.SCENE_REGISTER, req.smsCode());
        }
        if (userRepository.existsByPhone(req.phone())) {
            throw new BizException(ErrorCode.CONFLICT, "该手机号已注册,请直接登录");
        }

        User user = new User();
        user.setPhone(req.phone());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.phone());
        user = userRepository.save(user);

        Organization org = new Organization();
        org.setName(req.orgName());
        org = orgRepository.save(org);

        Member member = new Member();
        member.setOrgId(org.getId());
        member.setUserId(user.getId());
        member.setRole(Role.ADMIN);
        member.setStatus(1);
        memberRepository.save(member);

        return buildAuthResponse(user.getId(), org.getId(), Role.ADMIN.name());
    }

    /**
     * 登录:校验密码,取用户的首个组织身份签发 token。
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "手机号或密码错误"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "手机号或密码错误");
        }

        Member member = memberRepository.findByUserId(user.getId()).stream()
                .filter(m -> m.getStatus() == 1)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "该用户未加入任何组织"));

        return buildAuthResponse(user.getId(), member.getOrgId(), member.getRole().name());
    }

    /**
     * 刷新 access token。
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        final Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED, "登录已过期,请重新登录");
        }
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无效的刷新令牌");
        }

        Long userId = Long.valueOf(claims.getSubject());
        Member member = memberRepository.findByUserId(userId).stream()
                .filter(m -> m.getStatus() == 1)
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "该用户未加入任何组织"));

        return buildAuthResponse(userId, member.getOrgId(), member.getRole().name());
    }

    private AuthResponse buildAuthResponse(Long userId, Long orgId, String role) {
        String access = jwtService.generateAccessToken(userId, orgId, role);
        String refresh = jwtService.generateRefreshToken(userId);
        return new AuthResponse(userId, orgId, role, access, refresh, jwtService.getAccessTtlSeconds());
    }
}
