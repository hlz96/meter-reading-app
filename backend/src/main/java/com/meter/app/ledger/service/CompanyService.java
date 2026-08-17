package com.meter.app.ledger.service;

import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.ledger.dto.CompanyRequest;
import com.meter.app.ledger.dto.CompanyResponse;
import com.meter.app.ledger.entity.Company;
import com.meter.app.ledger.repository.CompanyRepository;
import com.meter.app.ledger.repository.MeterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final MeterRepository meterRepository;

    public CompanyService(CompanyRepository companyRepository, MeterRepository meterRepository) {
        this.companyRepository = companyRepository;
        this.meterRepository = meterRepository;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return companyRepository.findByOrgIdOrderByIdDesc(CurrentUser.orgId())
                .stream().map(CompanyResponse::from).toList();
    }

    @Transactional
    public CompanyResponse create(CompanyRequest req) {
        Long orgId = CurrentUser.orgId();
        if (companyRepository.existsByOrgIdAndName(orgId, req.name())) {
            throw new BizException(ErrorCode.CONFLICT, "公司名称已存在: " + req.name());
        }
        Company c = new Company();
        c.setOrgId(orgId);
        apply(c, req);
        return CompanyResponse.from(companyRepository.save(c));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest req) {
        Company c = mustGet(id);
        // 改名撞其他公司才报冲突
        if (!c.getName().equals(req.name())
                && companyRepository.existsByOrgIdAndName(c.getOrgId(), req.name())) {
            throw new BizException(ErrorCode.CONFLICT, "公司名称已存在: " + req.name());
        }
        apply(c, req);
        return CompanyResponse.from(companyRepository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        Company c = mustGet(id);
        // 有表计挂在该公司下时禁止删除,避免台账悬空
        if (meterRepository.existsByCompanyId(c.getId())) {
            throw new BizException(ErrorCode.CONFLICT, "该公司下仍有表计,不能删除");
        }
        companyRepository.delete(c);
    }

    private Company mustGet(Long id) {
        return companyRepository.findByIdAndOrgId(id, CurrentUser.orgId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "公司不存在"));
    }

    private void apply(Company c, CompanyRequest req) {
        c.setName(req.name());
        c.setContact(req.contact());
        c.setPhone(req.phone());
        c.setRemark(req.remark());
    }
}
