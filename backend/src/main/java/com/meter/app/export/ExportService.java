package com.meter.app.export;

import com.meter.app.ledger.dto.MeterResponse;
import com.meter.app.ledger.service.MeterService;
import com.meter.app.reading.dto.ReadingResponse;
import com.meter.app.reading.service.ReadingService;
import com.meter.app.report.dto.DunningResponse;
import com.meter.app.report.service.ReportService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 台账/读数/催缴单导出(TRD §4.3/§4.5)。复用现有 service 的查询(含 orgId 隔离与 READER 范围),仅负责转 Excel。
 */
@Service
public class ExportService {

    private final MeterService meterService;
    private final ReadingService readingService;
    private final ReportService reportService;

    public ExportService(MeterService meterService,
                         ReadingService readingService,
                         ReportService reportService) {
        this.meterService = meterService;
        this.readingService = readingService;
        this.reportService = reportService;
    }

    /** 导出本组织表计台账。 */
    public byte[] exportMeters() {
        List<List<String>> head = heads("表计ID", "公司ID", "表名", "类型", "初始底数", "倍率", "位置", "状态");
        List<List<Object>> rows = new ArrayList<>();
        for (MeterResponse m : meterService.list(null, null, null)) {
            List<Object> row = new ArrayList<>();
            row.add(m.id());
            row.add(m.companyId());
            row.add(m.name());
            row.add(typeText(m.type()));
            row.add(m.initialReading());
            row.add(m.ratio());
            row.add(m.location());
            row.add(m.status() == 1 ? "启用" : "停用");
            rows.add(row);
        }
        return ExcelUtil.write(head, rows);
    }

    /** 导出某周期读数(READER 只导出被分配公司,与列表口径一致)。 */
    public byte[] exportReadings(Long periodId) {
        List<List<String>> head = heads("读数ID", "表计ID", "周期ID", "上期", "本期", "用量", "异常", "审核状态", "审核备注");
        List<List<Object>> rows = new ArrayList<>();
        for (ReadingResponse r : readingService.list(periodId, null, null)) {
            List<Object> row = new ArrayList<>();
            row.add(r.id());
            row.add(r.meterId());
            row.add(r.periodId());
            row.add(r.prevReading());
            row.add(r.currReading());
            row.add(r.usageAmount());
            row.add(r.isAbnormal() ? (r.abnormalType() == null ? "异常" : r.abnormalType()) : "正常");
            row.add(auditText(r.auditStatus()));
            row.add(r.auditRemark());
            rows.add(row);
        }
        return ExcelUtil.write(head, rows);
    }

    /** 导出某公司催缴单。复用 ReportService.dunning,筛出该公司(无数据则导出零值单)。 */
    public byte[] exportDunning(Long periodId, Long companyId) {
        DunningResponse dunning = reportService.dunning(periodId);
        DunningResponse.Row target = dunning.rows().stream()
                .filter(r -> r.companyId().equals(companyId))
                .findFirst()
                .orElse(new DunningResponse.Row(companyId,
                        BigDecimal.ZERO, null, BigDecimal.ZERO, null, BigDecimal.ZERO));

        List<List<String>> head = heads("公司ID", "电量", "电费", "水量", "水费", "合计");
        List<Object> row = new ArrayList<>();
        row.add(target.companyId());
        row.add(target.elecUsage());
        row.add(feeText(target.elecFee()));
        row.add(target.waterUsage());
        row.add(feeText(target.waterFee()));
        row.add(target.totalFee());
        List<List<Object>> rows = new ArrayList<>();
        rows.add(row);
        return ExcelUtil.write(head, rows);
    }

    private Object feeText(BigDecimal fee) {
        return fee == null ? "未定价" : fee;
    }

    private List<List<String>> heads(String... names) {
        List<List<String>> head = new ArrayList<>();
        for (String n : names) head.add(List.of(n));
        return head;
    }

    private String typeText(Integer type) {
        if (type == null) return "";
        return type == 1 ? "电表" : type == 2 ? "水表" : String.valueOf(type);
    }

    private String auditText(Integer s) {
        if (s == null) return "";
        return switch (s) {
            case 1 -> "待审核";
            case 2 -> "已通过";
            case 3 -> "已驳回";
            default -> String.valueOf(s);
        };
    }
}
