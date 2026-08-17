package com.meter.app.org.service;

import com.meter.app.audit.service.AuditLogService;
import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.org.dto.MemberResponse;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 组织成员管理(TRD 2.2)。改角色仅管理员可操作,并写审计。
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final AuditLogService auditLogService;

    public MemberService(MemberRepository memberRepository, AuditLogService auditLogService) {
        this.memberRepository = memberRepository;
        this.auditLogService = auditLogService;
    }

    /** 列出本组织所有成员。 */
    @Transactional(readOnly = true)
    public List<MemberResponse> list() {
        return memberRepository.findByOrgId(CurrentUser.orgId())
                .stream().map(MemberResponse::from).toList();
    }

    /** 修改成员角色。不允许改自己,避免误把唯一管理员降级。 */
    @Transactional
    public MemberResponse changeRole(Long memberId, Role role) {
        Long orgId = CurrentUser.orgId();
        Member member = memberRepository.findByIdAndOrgId(memberId, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "成员不存在"));
        if (member.getUserId().equals(CurrentUser.userId())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "不能修改自己的角色");
        }
        Role oldRole = member.getRole();
        member.setRole(role);
        Member saved = memberRepository.save(member);
        auditLogService.record(AuditLogService.MEMBER_ROLE_CHANGE, "member:" + memberId,
                Map.of("oldRole", oldRole.name(), "newRole", role.name()));
        return MemberResponse.from(saved);
    }
}
