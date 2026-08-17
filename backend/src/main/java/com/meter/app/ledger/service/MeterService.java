package com.meter.app.ledger.service;

import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.ledger.dto.MeterRequest;
import com.meter.app.ledger.dto.MeterResponse;
import com.meter.app.ledger.entity.Meter;
import com.meter.app.ledger.repository.CompanyRepository;
import com.meter.app.ledger.repository.MeterRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MeterService {

    private final MeterRepository meterRepository;
    private final CompanyRepository companyRepository;

    public MeterService(MeterRepository meterRepository, CompanyRepository companyRepository) {
        this.meterRepository = meterRepository;
        this.companyRepository = companyRepository;
    }

    /** 按公司/类型/状态动态筛选,均可选。强制 orgId 隔离。 */
    @Transactional(readOnly = true)
    public List<MeterResponse> list(Long companyId, Integer type, Integer status) {
        Long orgId = CurrentUser.orgId();
        Specification<Meter> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("orgId"), orgId));
            if (companyId != null) ps.add(cb.equal(root.get("companyId"), companyId));
            if (type != null) ps.add(cb.equal(root.get("type"), type));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return meterRepository.findAll(spec).stream().map(MeterResponse::from).toList();
    }

    @Transactional
    public MeterResponse create(MeterRequest req) {
        Long orgId = CurrentUser.orgId();
        verifyCompanyBelongs(req.companyId(), orgId);

        Meter m = new Meter();
        m.setOrgId(orgId);
        apply(m, req);
        return MeterResponse.from(meterRepository.save(m));
    }

    @Transactional
    public MeterResponse update(Long id, MeterRequest req) {
        Meter m = mustGet(id);
        verifyCompanyBelongs(req.companyId(), m.getOrgId());
        apply(m, req);
        return MeterResponse.from(meterRepository.save(m));
    }

    @Transactional
    public void delete(Long id) {
        meterRepository.delete(mustGet(id));
    }

    private Meter mustGet(Long id) {
        return meterRepository.findByIdAndOrgId(id, CurrentUser.orgId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "表计不存在"));
    }

    /** 校验目标公司存在且属于当前组织,防止把表计挂到别组织的公司上。 */
    private void verifyCompanyBelongs(Long companyId, Long orgId) {
        companyRepository.findByIdAndOrgId(companyId, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "所属公司不存在"));
    }

    private void apply(Meter m, MeterRequest req) {
        m.setCompanyId(req.companyId());
        m.setName(req.name());
        m.setType(req.type());
        m.setInitialReading(req.initialReading());
        m.setRatio(req.ratio());
        m.setLocation(req.location());
        m.setStatus(req.status() == null ? 1 : req.status());
    }
}
