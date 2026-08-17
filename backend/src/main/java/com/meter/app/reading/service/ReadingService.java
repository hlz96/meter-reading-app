package com.meter.app.reading.service;

import com.meter.app.audit.service.AuditLogService;
import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.ledger.entity.Meter;
import com.meter.app.ledger.repository.MeterRepository;
import com.meter.app.reader.service.ReaderScopeService;
import com.meter.app.reading.dto.BatchResult;
import com.meter.app.reading.dto.ReadingRequest;
import com.meter.app.reading.dto.ReadingResponse;
import com.meter.app.reading.entity.Period;
import com.meter.app.reading.entity.Reading;
import com.meter.app.reading.repository.PeriodRepository;
import com.meter.app.reading.repository.ReadingRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ReadingService {

    /** 用量超过历史均值该倍数判为突增。 */
    private static final BigDecimal SPIKE_FACTOR = new BigDecimal("3");
    static final String ABNORMAL_BACKWARD = "BACKWARD";
    static final String ABNORMAL_SPIKE = "SPIKE";
    static final int AUDIT_PENDING = 1;
    static final int AUDIT_APPROVED = 2;
    static final int AUDIT_REJECTED = 3;
    private static final int BATCH_MAX = 500;

    private final ReadingRepository readingRepository;
    private final MeterRepository meterRepository;
    private final PeriodRepository periodRepository;
    private final ReaderScopeService readerScopeService;
    private final AuditLogService auditLogService;
    /** 自注入代理:批量提交时经代理调 submit,保证每条走各自独立事务(避免自调用绕过 @Transactional)。 */
    private final ReadingService self;

    public ReadingService(ReadingRepository readingRepository,
                          MeterRepository meterRepository,
                          PeriodRepository periodRepository,
                          ReaderScopeService readerScopeService,
                          AuditLogService auditLogService,
                          @Lazy ReadingService self) {
        this.readingRepository = readingRepository;
        this.meterRepository = meterRepository;
        this.periodRepository = periodRepository;
        this.readerScopeService = readerScopeService;
        this.auditLogService = auditLogService;
        this.self = self;
    }

    /**
     * 按周期查读数,支持按审核状态/公司筛选。
     * READER 只能看到被分配公司的读数(TRD §5.4)。
     */
    @Transactional(readOnly = true)
    public List<ReadingResponse> list(Long periodId, Integer auditStatus, Long companyId) {
        Long orgId = CurrentUser.orgId();
        Specification<Reading> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("orgId"), orgId));
            if (periodId != null) ps.add(cb.equal(root.get("periodId"), periodId));
            if (auditStatus != null) ps.add(cb.equal(root.get("auditStatus"), auditStatus));
            if (companyId != null) ps.add(cb.equal(root.get("companyId"), companyId));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        spec = spec.and(readerScopeService.companyScopeSpec());
        return readingRepository.findAll(spec).stream().map(ReadingResponse::from).toList();
    }

    /**
     * 提交读数:算用量、异常校验,初始为待审核。
     * 幂等:clientUuid 已存在则直接返回原记录(离线批量重传安全)。
     * 同一表+周期已抄:采用最新覆盖(TRD 决策4)。
     */
    @Transactional
    public ReadingResponse submit(ReadingRequest req) {
        Long orgId = CurrentUser.orgId();

        Meter meter = meterRepository.findByIdAndOrgId(req.meterId(), orgId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "表计不存在"));
        Period period = periodRepository.findByIdAndOrgId(req.periodId(), orgId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "周期不存在"));

        // READER 只能为被分配公司抄表(TRD §5.4)
        readerScopeService.assertCanSubmitForCompany(meter.getCompanyId());

        // 已结算周期不可再录入/修改读数
        if (period.getStatus() == PeriodService.STATUS_SETTLED) {
            throw new BizException(ErrorCode.CONFLICT, "该周期已结算,不能再录入读数");
        }

        // 同表同周期已存在 → 最新覆盖;幂等:clientUuid 相同直接返回原记录
        Reading r = readingRepository.findByMeterIdAndPeriodId(meter.getId(), period.getId())
                .orElse(null);
        if (r != null && req.clientUuid() != null && !req.clientUuid().isBlank()
                && req.clientUuid().equals(r.getClientUuid())) {
            return ReadingResponse.from(r);
        }
        boolean overwrite = r != null;
        BigDecimal oldCurr = overwrite ? r.getCurrReading() : null;
        Integer oldStatus = overwrite ? r.getAuditStatus() : null;
        if (r == null) {
            r = new Reading();
        }
        return recomputeAndSave(r, meter, period, req, overwrite, oldCurr, oldStatus);
    }

    /**
     * 落库读数并计算用量/异常,置为待审核。提交(submit)与修正(update)共用。
     * overwrite=true 时把旧值快照写入审计(TRD §5.2 最新覆盖)。
     */
    private ReadingResponse recomputeAndSave(Reading r, Meter meter, Period period,
                                             ReadingRequest req, boolean overwrite,
                                             BigDecimal oldCurr, Integer oldStatus) {
        BigDecimal prev = resolvePrevReading(meter, period);
        BigDecimal curr = req.currReading();

        r.setOrgId(meter.getOrgId());
        r.setMeterId(meter.getId());
        r.setPeriodId(period.getId());
        r.setCompanyId(meter.getCompanyId());
        r.setType(meter.getType());
        r.setPrevReading(prev);
        r.setCurrReading(curr);
        r.setPhotoUrl(req.photoUrl());
        r.setClientUuid(req.clientUuid());
        r.setRemark(req.remark());
        r.setReaderId(CurrentUser.userId());
        r.setReadAt(LocalDateTime.now());
        // 覆盖后重置为待审核(TRD §5.2:覆盖旧值后重新走审核)
        r.setAuditStatus(AUDIT_PENDING);
        r.setAuditorId(null);
        r.setAuditedAt(null);
        r.setAuditRemark(null);

        // 用量 =(本期 - 上期)× 倍率;倒退时用量记为 0,避免负值污染统计
        boolean backward = curr.compareTo(prev) < 0;
        BigDecimal usage = backward
                ? BigDecimal.ZERO
                : curr.subtract(prev).multiply(meter.getRatio());
        r.setUsageAmount(usage);

        applyAbnormal(r, meter, backward, usage);

        Reading saved = readingRepository.save(r);
        // 覆盖旧值写审计(TRD §5.2)
        if (overwrite) {
            auditLogService.record(AuditLogService.READING_OVERWRITE, "reading:" + saved.getId(),
                    Map.of("oldCurr", String.valueOf(oldCurr),
                            "newCurr", String.valueOf(curr),
                            "oldStatus", String.valueOf(oldStatus)));
        }
        return ReadingResponse.from(saved);
    }

    /**
     * 修正读数(TRD §4.4 PUT /readings/{id})。按记录 id 定位,meterId/periodId 以库中为准。
     * 校验周期未结算 + READER 公司范围,重算用量并重置为待审核。
     */
    @Transactional
    public ReadingResponse update(Long id, ReadingRequest req) {
        Long orgId = CurrentUser.orgId();
        Reading r = readingRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "读数记录不存在"));
        Meter meter = meterRepository.findByIdAndOrgId(r.getMeterId(), orgId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "表计不存在"));
        Period period = periodRepository.findByIdAndOrgId(r.getPeriodId(), orgId)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "周期不存在"));

        readerScopeService.assertCanSubmitForCompany(meter.getCompanyId());
        if (period.getStatus() == PeriodService.STATUS_SETTLED) {
            throw new BizException(ErrorCode.CONFLICT, "该周期已结算,不能再修改读数");
        }
        return recomputeAndSave(r, meter, period, req, true, r.getCurrReading(), r.getAuditStatus());
    }

    /**
     * 批量提交读数(TRD §4.4,离线同步用)。逐条提交,单条失败不影响其余。
     * 刻意不加 @Transactional:经自注入代理 self.submit 让每条走各自独立事务,
     * 某条回滚不波及整批(clientUuid 幂等使重传安全)。
     */
    public BatchResult submitBatch(List<ReadingRequest> items) {
        if (items == null || items.isEmpty()) {
            return new BatchResult(0, 0, List.of());
        }
        if (items.size() > BATCH_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "单批最多 " + BATCH_MAX + " 条");
        }
        int success = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            try {
                self.submit(items.get(i));
                success++;
            } catch (BizException e) {
                errors.add("第" + (i + 1) + "条: " + e.getMessage());
            }
        }
        return new BatchResult(success, errors.size(), errors);
    }

    /**
     * 审核读数:通过或驳回。仅管理员可操作(TRD 决策3)。
     */
    @Transactional
    public ReadingResponse audit(Long id, boolean approved, String remark) {
        requireAdmin();
        Reading r = readingRepository.findByIdAndOrgId(id, CurrentUser.orgId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "读数记录不存在"));
        applyAudit(r, approved, remark);
        return ReadingResponse.from(readingRepository.save(r));
    }

    /**
     * 批量审核(TRD §4.4.1)。仅管理员;越权/他组织的 id 静默忽略。
     * 返回成功处理的记录数。
     */
    @Transactional
    public int auditBatch(List<Long> ids, boolean approved, String remark) {
        requireAdmin();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Reading> rs = readingRepository.findByOrgIdAndIdIn(CurrentUser.orgId(), ids);
        for (Reading r : rs) {
            applyAudit(r, approved, remark);
        }
        readingRepository.saveAll(rs);
        return rs.size();
    }

    private void requireAdmin() {
        if (!"ADMIN".equals(CurrentUser.role())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有管理员可以审核");
        }
    }

    /** 置审核状态并写审计。 */
    private void applyAudit(Reading r, boolean approved, String remark) {
        int oldStatus = r.getAuditStatus();
        r.setAuditStatus(approved ? AUDIT_APPROVED : AUDIT_REJECTED);
        r.setAuditorId(CurrentUser.userId());
        r.setAuditedAt(LocalDateTime.now());
        if (remark != null && !remark.isBlank()) {
            r.setAuditRemark(remark);
        }
        auditLogService.record(AuditLogService.READING_AUDIT, "reading:" + r.getId(),
                Map.of("approved", approved,
                        "oldStatus", String.valueOf(oldStatus),
                        "newStatus", String.valueOf(r.getAuditStatus()),
                        "remark", remark == null ? "" : remark));
    }

    /**
     * 上期读数:优先按周期 start_date 取前序周期读数;
     * 周期未填日期时退回按 period_id 排序;都无则取表计初始底数。
     */
    private BigDecimal resolvePrevReading(Meter meter, Period period) {
        if (period.getStartDate() != null) {
            List<Reading> prev = readingRepository.findPrevByStartDate(
                    meter.getId(), period.getStartDate(), PageRequest.of(0, 1));
            if (!prev.isEmpty()) {
                return prev.get(0).getCurrReading();
            }
            return meter.getInitialReading();
        }
        // 兜底:周期没填起始日期,按 period_id 顺序,只取已通过审核的记录作基准
        return readingRepository
                .findFirstByMeterIdAndAuditStatusAndPeriodIdLessThanOrderByPeriodIdDesc(
                        meter.getId(), AUDIT_APPROVED, period.getId())
                .map(Reading::getCurrReading)
                .orElse(meter.getInitialReading());
    }

    /** 异常校验:倒退优先;否则看是否超历史均值 N 倍。 */
    private void applyAbnormal(Reading r, Meter meter, boolean backward, BigDecimal usage) {
        if (backward) {
            r.setIsAbnormal(1);
            r.setAbnormalType(ABNORMAL_BACKWARD);
            return;
        }
        BigDecimal avg = readingRepository.avgUsageExcludingPeriod(meter.getId(), r.getPeriodId());
        if (avg != null && avg.signum() > 0
                && usage.compareTo(avg.multiply(SPIKE_FACTOR)) > 0) {
            r.setIsAbnormal(1);
            r.setAbnormalType(ABNORMAL_SPIKE);
        } else {
            r.setIsAbnormal(0);
            r.setAbnormalType(null);
        }
    }
}
