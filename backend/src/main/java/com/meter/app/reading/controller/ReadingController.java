package com.meter.app.reading.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.reading.dto.BatchResult;
import com.meter.app.reading.dto.ReadingRequest;
import com.meter.app.reading.dto.ReadingResponse;
import com.meter.app.reading.service.ReadingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/readings")
public class ReadingController {

    private final ReadingService readingService;

    public ReadingController(ReadingService readingService) {
        this.readingService = readingService;
    }

    /** 按周期查读数,可选按审核状态/公司筛选(READER 只见被分配公司)。 */
    @GetMapping
    public ApiResponse<List<ReadingResponse>> list(
            @RequestParam Long periodId,
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Long companyId) {
        return ApiResponse.ok(readingService.list(periodId, auditStatus, companyId));
    }

    /** 提交单条读数(管理员或抄表员)。 */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public ApiResponse<ReadingResponse> submit(@Valid @RequestBody ReadingRequest req) {
        return ApiResponse.ok(readingService.submit(req));
    }

    /** 批量提交读数(离线同步,管理员或抄表员)。逐条处理,返回成功/失败明细。 */
    @PostMapping("/batch")
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public ApiResponse<BatchResult> submitBatch(@Valid @RequestBody BatchReadingRequest req) {
        return ApiResponse.ok(readingService.submitBatch(req.items()));
    }

    /** 修正读数(管理员或抄表员)。按记录 id 定位,重算用量并重置为待审核。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public ApiResponse<ReadingResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ReadingRequest req) {
        return ApiResponse.ok(readingService.update(id, req));
    }

    /** 审核:通过或驳回(仅管理员)。 */
    @PostMapping("/{id}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ReadingResponse> audit(@PathVariable Long id,
                                              @Valid @RequestBody AuditRequest req) {
        return ApiResponse.ok(readingService.audit(id, req.approved(), req.remark()));
    }

    /** 批量审核(仅管理员)。返回成功处理条数。 */
    @PostMapping("/audit/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Integer>> auditBatch(@Valid @RequestBody BatchAuditRequest req) {
        int count = readingService.auditBatch(req.ids(), req.approved(), req.remark());
        return ApiResponse.ok(Map.of("processed", count));
    }

    public record AuditRequest(@NotNull Boolean approved, String remark) {
    }

    public record BatchAuditRequest(@NotEmpty List<Long> ids, @NotNull Boolean approved, String remark) {
    }

    public record BatchReadingRequest(@NotEmpty @Valid List<ReadingRequest> items) {
    }
}
