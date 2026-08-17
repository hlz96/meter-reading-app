package com.meter.app.org.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.org.dto.InviteRequest;
import com.meter.app.org.dto.InviteResponse;
import com.meter.app.org.dto.JoinRequest;
import com.meter.app.org.dto.JoinResponse;
import com.meter.app.org.dto.MemberResponse;
import com.meter.app.org.entity.Invitation;
import com.meter.app.org.entity.Role;
import com.meter.app.org.service.InvitationService;
import com.meter.app.org.service.MemberService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 组织成员管理接口(TRD 2.2 / §4.1)。
 */
@RestController
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final InvitationService invitationService;

    public MemberController(MemberService memberService, InvitationService invitationService) {
        this.memberService = memberService;
        this.invitationService = invitationService;
    }

    /** 列出本组织成员(管理员)。 */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MemberResponse>> list() {
        return ApiResponse.ok(memberService.list());
    }

    /** 修改成员角色(管理员)。 */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MemberResponse> changeRole(@PathVariable Long id,
                                                  @RequestBody RoleRequest req) {
        return ApiResponse.ok(memberService.changeRole(id, parseRole(req.role())));
    }

    /** 生成邀请码(管理员)。 */
    @PostMapping("/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<InviteResponse> invite(@Valid @RequestBody InviteRequest req) {
        Invitation inv = invitationService.invite(parseRole(req.role()));
        long expiresIn = Duration.between(LocalDateTime.now(), inv.getExpiresAt()).getSeconds();
        return ApiResponse.ok(InviteResponse.from(inv, expiresIn));
    }

    /** 凭邀请码加入组织(需登录;加入后需以新组织身份重新登录)。 */
    @PostMapping("/join")
    public ApiResponse<JoinResponse> join(@Valid @RequestBody JoinRequest req) {
        return ApiResponse.ok(JoinResponse.from(invitationService.join(req.code())));
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法角色: " + role);
        }
    }

    public record RoleRequest(@NotBlank String role) {
    }
}
