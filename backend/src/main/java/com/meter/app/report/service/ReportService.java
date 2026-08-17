package com.meter.app.report.service;

import com.meter.app.auth.CurrentUser;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.reading.entity.Period;
import com.meter.app.reading.repository.PeriodRepository;
import com.meter.app.reading.repository.ReadingRepository;
import com.meter.app.report.dto.ChartResponse;
import com.meter.app.report.dto.DunningResponse;
import com.meter.app.report.dto.ElecWaterPoint;
import com.meter.app.report.dto.RatioSlice;
import com.meter.app.report.dto.SummaryResponse;
import com.meter.app.report.dto.TrendPoint;
import com.meter.app.report.dto.UsageAgg;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报表/催单聚合(TRD §5.5)。只统计已通过审核的读数;费用=用量×当期费率。
 * 仅供 ADMIN/VIEWER 使用(账单口径,不走 READER 公司范围)。
 */
@Service
public class ReportService {

    private static final int TYPE_ELEC = 1;
    private static final int TYPE_WATER = 2;
    private static final int AUDIT_PENDING = 1;

    private final ReadingRepository readingRepository;
    private final PeriodRepository periodRepository;

    public ReportService(ReadingRepository readingRepository, PeriodRepository periodRepository) {
        this.readingRepository = readingRepository;
        this.periodRepository = periodRepository;
    }

    /** 各公司各类型用量/费用汇总。 */
    @Transactional(readOnly = true)
    public SummaryResponse summary(Long periodId) {
        Long orgId = CurrentUser.orgId();
        Period period = mustGetPeriod(periodId, orgId);
        long pending = readingRepository.countByOrgIdAndPeriodIdAndAuditStatus(orgId, periodId, AUDIT_PENDING);

        List<SummaryResponse.Row> rows = new ArrayList<>();
        for (UsageAgg agg : readingRepository.sumUsageByCompanyType(orgId, periodId)) {
            BigDecimal fee = calcFee(agg.type(), agg.usage(), period);
            rows.add(new SummaryResponse.Row(agg.companyId(), agg.type(), agg.usage(), fee));
        }
        return new SummaryResponse(periodId, pending, rows);
    }

    /** 按公司归集催缴数据(电费+水费+合计)。 */
    @Transactional(readOnly = true)
    public DunningResponse dunning(Long periodId) {
        Long orgId = CurrentUser.orgId();
        Period period = mustGetPeriod(periodId, orgId);
        long pending = readingRepository.countByOrgIdAndPeriodIdAndAuditStatus(orgId, periodId, AUDIT_PENDING);

        // 按公司归集(保持出现顺序)
        Map<Long, DunningAccumulator> byCompany = new LinkedHashMap<>();
        for (UsageAgg agg : readingRepository.sumUsageByCompanyType(orgId, periodId)) {
            DunningAccumulator acc = byCompany.computeIfAbsent(agg.companyId(), k -> new DunningAccumulator());
            BigDecimal fee = calcFee(agg.type(), agg.usage(), period);
            if (agg.type() == TYPE_ELEC) {
                acc.elecUsage = agg.usage();
                acc.elecFee = fee;
            } else if (agg.type() == TYPE_WATER) {
                acc.waterUsage = agg.usage();
                acc.waterFee = fee;
            }
        }

        List<DunningResponse.Row> rows = new ArrayList<>();
        for (Map.Entry<Long, DunningAccumulator> e : byCompany.entrySet()) {
            DunningAccumulator a = e.getValue();
            BigDecimal total = BigDecimal.ZERO;
            if (a.elecFee != null) total = total.add(a.elecFee);
            if (a.waterFee != null) total = total.add(a.waterFee);
            rows.add(new DunningResponse.Row(e.getKey(),
                    a.elecUsage, a.elecFee, a.waterUsage, a.waterFee,
                    total.setScale(2, RoundingMode.HALF_UP)));
        }
        return new DunningResponse(periodId, pending, rows);
    }

    /** 费用 = 用量 × 当期费率;费率未填返回 null(未定价)。 */
    private BigDecimal calcFee(int type, BigDecimal usage, Period period) {
        BigDecimal price = type == TYPE_ELEC ? period.getElecPrice()
                : type == TYPE_WATER ? period.getWaterPrice() : null;
        if (price == null || usage == null) {
            return null;
        }
        return usage.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    /** 趋势图:某公司跨周期用量(type 可空)。 */
    @Transactional(readOnly = true)
    public ChartResponse trend(Long companyId, Integer type) {
        if (companyId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "趋势图需指定公司");
        }
        List<TrendPoint> data = readingRepository.usageTrendByCompany(CurrentUser.orgId(), companyId, type);
        return new ChartResponse("trend", data);
    }

    /** 占比图:某周期各公司用量占比。 */
    @Transactional(readOnly = true)
    public ChartResponse ratio(Long periodId) {
        Long orgId = CurrentUser.orgId();
        mustGetPeriod(periodId, orgId);
        // 按公司汇总用量(不分电水)
        Map<Long, BigDecimal> byCompany = new LinkedHashMap<>();
        for (UsageAgg agg : readingRepository.sumUsageByCompanyType(orgId, periodId)) {
            byCompany.merge(agg.companyId(), agg.usage(), BigDecimal::add);
        }
        BigDecimal total = byCompany.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<RatioSlice> data = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : byCompany.entrySet()) {
            BigDecimal percent = total.signum() == 0 ? BigDecimal.ZERO
                    : e.getValue().multiply(new BigDecimal("100"))
                    .divide(total, 2, RoundingMode.HALF_UP);
            data.add(new RatioSlice(e.getKey(), e.getValue(), percent));
        }
        return new ChartResponse("ratio", data);
    }

    /** 电水对比图:某周期各公司电量 vs 水量。 */
    @Transactional(readOnly = true)
    public ChartResponse elecWater(Long periodId) {
        Long orgId = CurrentUser.orgId();
        mustGetPeriod(periodId, orgId);
        Map<Long, BigDecimal[]> byCompany = new LinkedHashMap<>();
        for (UsageAgg agg : readingRepository.sumUsageByCompanyType(orgId, periodId)) {
            BigDecimal[] pair = byCompany.computeIfAbsent(agg.companyId(),
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (agg.type() == TYPE_ELEC) pair[0] = agg.usage();
            else if (agg.type() == TYPE_WATER) pair[1] = agg.usage();
        }
        List<ElecWaterPoint> data = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal[]> e : byCompany.entrySet()) {
            data.add(new ElecWaterPoint(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        return new ChartResponse("elec-water", data);
    }

    private Period mustGetPeriod(Long periodId, Long orgId) {
        return periodRepository.findByIdAndOrgId(periodId, orgId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "周期不存在"));
    }

    private static final class DunningAccumulator {
        BigDecimal elecUsage;
        BigDecimal elecFee;
        BigDecimal waterUsage;
        BigDecimal waterFee;
    }
}
