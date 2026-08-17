package com.meter.app.org.service;

import com.meter.app.audit.service.AuditLogService;
import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.org.entity.Invitation;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.InvitationRepository;
import com.meter.app.org.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 组织邀请码(TRD §4.1)。管理员生成一次性邀请码,被邀请人凭码加入。
 */
@Service
public class InvitationService {

    private static final int STATUS_UNUSED = 0;
    private static final int STATUS_USED = 1;
    private static final long TTL_SECONDS = 7 * 24 * 3600L; // 邀请码有效期 7 天

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public InvitationService(InvitationRepository invitationRepository,
                             MemberRepository memberRepository,
                             AuditLogService auditLogService) {
        this.invitationRepository = invitationRepository;
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
    }

    /** 管理员生成邀请码,指定加入后的角色。返回邀请码。 */
    @Transactional
    public Invitation invite(Role role) {
        Invitation inv = new Invitation();
        inv.setOrgId(CurrentUser.orgId());
        inv.setCode(UUID.randomUUID().toString().replace("-", ""));
        inv.setRole(role);
        inv.setExpiresAt(LocalDateTime.now().plusSeconds(TTL_SECONDS));
        inv.setStatus(STATUS_UNUSED);
        inv.setCreatedBy(CurrentUser.userId());
        return invitationRepository.save(inv);
    }

    /**
     * 凭邀请码加入组织。为当前登录用户在邀请码所属组织建成员(角色取邀请码里的 role)。
     * 一次性:成功后置邀请码为已用。
     */
    @Transactional
    public Member join(String code) {
        Invitation inv = invitationRepository.findByCode(code)
                .orElseThrow(() -> new BizException(ErrorCode.INVITATION_INVALID, "邀请码无效"));
        if (inv.getStatus() != STATUS_UNUSED) {
            throw new BizException(ErrorCode.INVITATION_INVALID, "邀请码已被使用或作废");
        }
        if (inv.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.INVITATION_INVALID, "邀请码已过期");
        }

        Long userId = CurrentUser.userId();
        // 注意:用邀请码所属组织 inv.getOrgId(),不是当前 token 的 orgId
        Long targetOrgId = inv.getOrgId();
        if (memberRepository.findByOrgIdAndUserId(targetOrgId, userId).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "你已是该组织成员");
        }

        Member member = new Member();
        member.setOrgId(targetOrgId);
        member.setUserId(userId);
        member.setRole(inv.getRole());
        member.setStatus(1);
        Member saved = memberRepository.save(member);

        inv.setStatus(STATUS_USED);
        invitationRepository.save(inv);

        auditLogService.record(AuditLogService.MEMBER_JOIN, "org:" + targetOrgId,
                Map.of("userId", String.valueOf(userId), "role", inv.getRole().name()));
        return saved;
    }
}
