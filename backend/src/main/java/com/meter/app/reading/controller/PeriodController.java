package com.meter.app.reading.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.reading.dto.PeriodRequest;
import com.meter.app.reading.dto.PeriodResponse;
import com.meter.app.reading.dto.TaskResponse;
import com.meter.app.reading.service.PeriodService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/periods")
public class PeriodController {

    private final PeriodService periodService;

    public PeriodController(PeriodService periodService) {
        this.periodService = periodService;
    }

    @GetMapping
    public ApiResponse<List<PeriodResponse>> list() {
        return ApiResponse.ok(periodService.list());
    }

    /** 待抄清单(登录即可;抄表员只见被分配公司的表)。 */
    @GetMapping("/{id}/tasks")
    public ApiResponse<TaskResponse> tasks(@PathVariable Long id) {
        return ApiResponse.ok(periodService.tasks(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PeriodResponse> create(@Valid @RequestBody PeriodRequest req) {
        return ApiResponse.ok(periodService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PeriodResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody PeriodRequest req) {
        return ApiResponse.ok(periodService.update(id, req));
    }

    @PostMapping("/{id}/settle")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PeriodResponse> settle(@PathVariable Long id) {
        return ApiResponse.ok(periodService.settle(id));
    }
}
