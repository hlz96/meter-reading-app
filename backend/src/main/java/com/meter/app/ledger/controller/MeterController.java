package com.meter.app.ledger.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.ledger.dto.MeterRequest;
import com.meter.app.ledger.dto.MeterResponse;
import com.meter.app.ledger.service.MeterService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/meters")
public class MeterController {

    private final MeterService meterService;

    public MeterController(MeterService meterService) {
        this.meterService = meterService;
    }

    /** 列表,支持按公司/类型/状态筛选(均可选)。 */
    @GetMapping
    public ApiResponse<List<MeterResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer status) {
        return ApiResponse.ok(meterService.list(companyId, type, status));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MeterResponse> create(@Valid @RequestBody MeterRequest req) {
        return ApiResponse.ok(meterService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MeterResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody MeterRequest req) {
        return ApiResponse.ok(meterService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        meterService.delete(id);
        return ApiResponse.ok();
    }
}
