package com.meter.app.reader.service;

import com.meter.app.audit.service.AuditLogService;
import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.ledger.repository.CompanyRepository;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import com.meter.app.reader.entity.ReaderCompany;
import com.meter.app.reader.repository.ReaderCompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 抄表员公司分配管理(TRD 决策⑤)。仅管理员可操作。
 */
@Service
public class ReaderService {

    private final MemberRepository memberRepository;
    private final CompanyRepository companyRepository;
    private final ReaderCompanyRepository readerCompanyRepository;
    private final AuditLogService auditLogService;

    public ReaderService(MemberRepository memberRepository,
                         CompanyRepository companyRepository,
                         ReaderCompanyRepository readerCompanyRepository,
                         AuditLogService auditLogService) {
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.readerCompanyRepository = readerCompanyRepository;
        this.auditLogService = auditLogService;
    }

    /** 查某抄表员被分配的公司 id 列表。 */
    @Transactional(readOnly = true)
    public List<Long> getCompanies(Long memberId) {
        Long orgId = CurrentUser.orgId();
        mustGetMember(memberId, orgId);
        return readerCompanyRepository.findByOrgIdAndMemberId(orgId, memberId).stream()
                .map(ReaderCompany::getCompanyId).toList();
    }

    /**
     * 设置某抄表员的公司分配(全量覆盖:先删旧,再插新)。
     * 校验:member 属本组织且角色为 READER;每个 company 属本组织。
     */
    @Transactional
    public List<Long> setCompanies(Long memberId, List<Long> companyIds) {
        Long orgId = CurrentUser.orgId();
        Member member = mustGetMember(memberId, orgId);
        if (member.getRole() != Role.READER) {
            throw new BizException(ErrorCode.PARAM_INVALID, "只能给抄表员(READER)分配公司");
        }
        List<Long> distinct = companyIds == null ? List.of() : companyIds.stream().distinct().toList();
        for (Long companyId : distinct) {
            companyRepository.findByIdAndOrgId(companyId, orgId)
                    .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "公司不存在: " + companyId));
        }

        List<Long> before = readerCompanyRepository.findByOrgIdAndMemberId(orgId, memberId).stream()
                .map(ReaderCompany::getCompanyId).toList();

        readerCompanyRepository.deleteByOrgIdAndMemberId(orgId, memberId);
        for (Long companyId : distinct) {
            ReaderCompany rc = new ReaderCompany();
            rc.setOrgId(orgId);
            rc.setMemberId(memberId);
            rc.setCompanyId(companyId);
            readerCompanyRepository.save(rc);
        }

        auditLogService.record(AuditLogService.READER_ASSIGN, "member:" + memberId,
                Map.of("before", before, "after", distinct));
        return distinct;
    }

    private Member mustGetMember(Long memberId, Long orgId) {
        return memberRepository.findByIdAndOrgId(memberId, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "成员不存在"));
    }
}
