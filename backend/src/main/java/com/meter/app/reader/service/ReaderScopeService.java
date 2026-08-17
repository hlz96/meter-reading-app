package com.meter.app.reader.service;

import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.org.entity.Member;
import com.meter.app.org.entity.Role;
import com.meter.app.org.repository.MemberRepository;
import com.meter.app.reader.entity.ReaderCompany;
import com.meter.app.reader.repository.ReaderCompanyRepository;
import com.meter.app.reading.entity.Reading;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * READER 数据范围解析(TRD §5.4,决策⑤)。横切件,被读数列表/提交/导入复用。
 *
 * ADMIN/VIEWER:不限制,可见全组织。
 * READER:只能看/写被分配公司(reader_company)下的表计;未分配则看不到任何数据。
 *
 * 注意:JWT 只有 userId+orgId+role,而 reader_company.member_id 指向 member.id,
 * 因此必须先 userId+orgId → Member → member.id → reader_company。
 */
@Service
public class ReaderScopeService {

    private final MemberRepository memberRepository;
    private final ReaderCompanyRepository readerCompanyRepository;

    public ReaderScopeService(MemberRepository memberRepository,
                              ReaderCompanyRepository readerCompanyRepository) {
        this.memberRepository = memberRepository;
        this.readerCompanyRepository = readerCompanyRepository;
    }

    /**
     * 当前用户可见的公司集合。
     * 返回 empty = 不限制(ADMIN/VIEWER 全组织);
     * 返回 present = 限制到该集合(READER,可能是空集=看不到任何数据)。
     */
    public Optional<Set<Long>> visibleCompanyIds() {
        if (!Role.READER.name().equals(CurrentUser.role())) {
            return Optional.empty();
        }
        Long orgId = CurrentUser.orgId();
        Member member = memberRepository.findByOrgIdAndUserId(orgId, CurrentUser.userId())
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "当前用户不属于该组织"));
        Set<Long> companyIds = readerCompanyRepository
                .findByOrgIdAndMemberId(orgId, member.getId()).stream()
                .map(ReaderCompany::getCompanyId)
                .collect(Collectors.toSet());
        return Optional.of(companyIds);
    }

    /** 提交/导入读数时校验:受限用户不能为不在分配范围内的公司抄表。 */
    public void assertCanSubmitForCompany(Long companyId) {
        Optional<Set<Long>> visible = visibleCompanyIds();
        if (visible.isPresent() && !visible.get().contains(companyId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权为该公司抄表");
        }
    }

    /**
     * 读数列表的公司范围过滤 Specification。
     * 不限制→恒真;受限非空→companyId IN (set);受限空集→恒假(查不到)。
     */
    public Specification<Reading> companyScopeSpec() {
        Optional<Set<Long>> visible = visibleCompanyIds();
        if (visible.isEmpty()) {
            return (root, query, cb) -> cb.conjunction();
        }
        Set<Long> ids = visible.get();
        if (ids.isEmpty()) {
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("companyId").in(ids);
    }
}
