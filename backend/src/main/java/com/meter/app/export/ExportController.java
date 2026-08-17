package com.meter.app.export;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 导出接口(TRD §4.3/§4.5)。返回 xlsx 字节流。
 */
@RestController
@RequestMapping("/api/v1")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /** 导出表计台账(ADMIN/VIEWER)。 */
    @GetMapping("/export/meters")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
    public ResponseEntity<byte[]> exportMeters() {
        return ExcelUtil.asAttachment(exportService.exportMeters(), "表计台账");
    }

    /** 导出某周期读数(ADMIN/VIEWER/READER,READER 只导出被分配公司)。 */
    @GetMapping("/export/readings")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER','READER')")
    public ResponseEntity<byte[]> exportReadings(@RequestParam Long periodId) {
        return ExcelUtil.asAttachment(exportService.exportReadings(periodId), "抄表记录");
    }

    /** 导出某公司催缴单(ADMIN/VIEWER)。 */
    @GetMapping("/dunning/{periodId}/company/{companyId}/export")
    @PreAuthorize("hasAnyRole('ADMIN','VIEWER')")
    public ResponseEntity<byte[]> exportDunning(@PathVariable Long periodId,
                                                @PathVariable Long companyId) {
        return ExcelUtil.asAttachment(exportService.exportDunning(periodId, companyId), "催缴单");
    }
}
