package com.meter.app.report.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.common.BizException;
import com.meter.app.common.ErrorCode;
import com.meter.app.report.dto.ChartResponse;
import com.meter.app.report.dto.DunningResponse;
import com.meter.app.report.dto.SummaryResponse;
import com.meter.app.report.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 报表与催单(TRD §4.5)。账单口径,仅 ADMIN/VIEWER 可访问。
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 各公司用量/费用汇总。 */
    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
    public ApiResponse<SummaryResponse> summary(@RequestParam Long periodId) {
        return ApiResponse.ok(reportService.summary(periodId));
    }

    /** 按公司归集的催缴数据。 */
    @GetMapping("/dunning/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
    public ApiResponse<DunningResponse> dunning(@PathVariable Long periodId) {
        return ApiResponse.ok(reportService.dunning(periodId));
    }

    /**
     * 图表聚合数据。type=trend(需 companyId,可选 type 电/水)/ ratio(需 periodId)/ elec-water(需 periodId)。
     */
    @GetMapping("/reports/charts")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
    public ApiResponse<ChartResponse> charts(@RequestParam String type,
                                             @RequestParam(required = false) Long periodId,
                                             @RequestParam(required = false) Long companyId,
                                             @RequestParam(name = "meterType", required = false) Integer meterType) {
        ChartResponse resp = switch (type) {
            case "trend" -> reportService.trend(companyId, meterType);
            case "ratio" -> reportService.ratio(requirePeriod(periodId));
            case "elec-water" -> reportService.elecWater(requirePeriod(periodId));
            default -> throw new BizException(ErrorCode.PARAM_INVALID, "不支持的图表类型: " + type);
        };
        return ApiResponse.ok(resp);
    }

    private Long requirePeriod(Long periodId) {
        if (periodId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该图表需指定 periodId");
        }
        return periodId;
    }
}
