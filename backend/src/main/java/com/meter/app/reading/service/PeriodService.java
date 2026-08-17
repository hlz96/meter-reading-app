package com.meter.app.reading.service;

import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.ledger.entity.Meter;
import com.meter.app.ledger.repository.MeterRepository;
import com.meter.app.reader.service.ReaderScopeService;
import com.meter.app.reading.dto.PeriodRequest;
import com.meter.app.reading.dto.PeriodResponse;
import com.meter.app.reading.dto.TaskItem;
import com.meter.app.reading.dto.TaskResponse;
import com.meter.app.reading.entity.Period;
import com.meter.app.reading.entity.Reading;
import com.meter.app.reading.repository.PeriodRepository;
import com.meter.app.reading.repository.ReadingRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PeriodService {

    static final int STATUS_OPEN = 1;      // 进行中
    static final int STATUS_SETTLED = 2;   // 已结算
    private static final int AUDIT_APPROVED = 2;
    private static final int METER_TYPE_ELEC = 1;
    private static final int METER_TYPE_WATER = 2;
    private static final int METER_ENABLED = 1;

    private final PeriodRepository periodRepository;
    private final ReadingRepository readingRepository;
    private final MeterRepository meterRepository;
    private final ReaderScopeService readerScopeService;

    public PeriodService(PeriodRepository periodRepository,
                         ReadingRepository readingRepository,
                         MeterRepository meterRepository,
                         ReaderScopeService readerScopeService) {
        this.periodRepository = periodRepository;
        this.readingRepository = readingRepository;
        this.meterRepository = meterRepository;
        this.readerScopeService = readerScopeService;
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        return periodRepository.findByOrgIdOrderByIdDesc(CurrentUser.orgId())
                .stream().map(PeriodResponse::from).toList();
    }

    @Transactional
    public PeriodResponse create(PeriodRequest req) {
        validateDates(req);
        Period p = new Period();
        p.setOrgId(CurrentUser.orgId());
        p.setStatus(STATUS_OPEN);
        apply(p, req);
        return PeriodResponse.from(periodRepository.save(p));
    }

    @Transactional
    public PeriodResponse update(Long id, PeriodRequest req) {
        Period p = mustGet(id);
        if (p.getStatus() == STATUS_SETTLED) {
            throw new BizException(ErrorCode.CONFLICT, "已结算周期不可修改");
        }
        validateDates(req);
        apply(p, req);
        return PeriodResponse.from(periodRepository.save(p));
    }

    /**
     * 标记周期为已结算(TRD §5.7)。结算前校验:
     * ① 该周期内读数全部审核通过;
     * ② 涉及的表类型对应费率已填(有电表读数则电价非空,有水表读数则水价非空)。
     * 空周期(无任何读数)允许结算。
     */
    @Transactional
    public PeriodResponse settle(Long id) {
        Period p = mustGet(id);
        Long orgId = CurrentUser.orgId();

        if (readingRepository.countByOrgIdAndPeriodIdAndAuditStatusNot(orgId, id, AUDIT_APPROVED) > 0) {
            throw new BizException(ErrorCode.PERIOD_NOT_SETTLEABLE, "存在未通过审核的读数,不能结算");
        }
        boolean hasElec = readingRepository.existsByOrgIdAndPeriodIdAndType(orgId, id, METER_TYPE_ELEC);
        if (hasElec && p.getElecPrice() == null) {
            throw new BizException(ErrorCode.PERIOD_NOT_SETTLEABLE, "本周期有电表读数,请先填写电价");
        }
        boolean hasWater = readingRepository.existsByOrgIdAndPeriodIdAndType(orgId, id, METER_TYPE_WATER);
        if (hasWater && p.getWaterPrice() == null) {
            throw new BizException(ErrorCode.PERIOD_NOT_SETTLEABLE, "本周期有水表读数,请先填写水价");
        }

        p.setStatus(STATUS_SETTLED);
        return PeriodResponse.from(periodRepository.save(p));
    }

    /**
     * 待抄清单(TRD §4.4)。遍历本组织启用表计,左连该周期读数,标注已抄/未抄。
     * READER 只见被分配公司的表计;未分配则清单为空。
     */
    @Transactional(readOnly = true)
    public TaskResponse tasks(Long periodId) {
        Long orgId = CurrentUser.orgId();
        mustGet(periodId); // 校验周期存在且属本组织

        Optional<Set<Long>> visible = readerScopeService.visibleCompanyIds();
        // READER 且无任何分配 → 空清单
        if (visible.isPresent() && visible.get().isEmpty()) {
            return new TaskResponse(periodId, 0, 0, List.of());
        }

        Specification<Meter> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("orgId"), orgId));
            ps.add(cb.equal(root.get("status"), METER_ENABLED));
            visible.ifPresent(ids -> ps.add(root.get("companyId").in(ids)));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Meter> meters = meterRepository.findAll(spec);

        Map<Long, Reading> byMeter = readingRepository.findByOrgIdAndPeriodId(orgId, periodId)
                .stream().collect(Collectors.toMap(Reading::getMeterId, Function.identity(), (a, b) -> a));

        List<TaskItem> items = new ArrayList<>();
        long done = 0;
        for (Meter m : meters) {
            Reading r = byMeter.get(m.getId());
            boolean isDone = r != null;
            if (isDone) done++;
            items.add(new TaskItem(m.getId(), m.getName(), m.getCompanyId(), m.getType(),
                    isDone, isDone ? r.getCurrReading() : null,
                    isDone ? r.getUsageAmount() : null,
                    isDone ? r.getAuditStatus() : null));
        }
        return new TaskResponse(periodId, items.size(), done, items);
    }

    private Period mustGet(Long id) {
        return periodRepository.findByIdAndOrgId(id, CurrentUser.orgId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "周期不存在"));
    }

    private void validateDates(PeriodRequest req) {
        if (req.startDate() != null && req.endDate() != null
                && req.endDate().isBefore(req.startDate())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束日期不能早于开始日期");
        }
    }

    private void apply(Period p, PeriodRequest req) {
        p.setName(req.name());
        p.setStartDate(req.startDate());
        p.setEndDate(req.endDate());
        p.setElecPrice(req.elecPrice());
        p.setWaterPrice(req.waterPrice());
    }
}
