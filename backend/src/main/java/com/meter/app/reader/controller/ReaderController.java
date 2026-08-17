package com.meter.app.reader.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.reader.service.ReaderService;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 抄表员公司分配接口(TRD §4.2,决策⑤)。仅管理员可操作。
 */
@RestController
@RequestMapping("/api/v1/readers")
public class ReaderController {

    private final ReaderService readerService;

    public ReaderController(ReaderService readerService) {
        this.readerService = readerService;
    }

    /** 查某抄表员被分配的公司。 */
    @GetMapping("/{memberId}/companies")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Long>> getCompanies(@PathVariable Long memberId) {
        return ApiResponse.ok(readerService.getCompanies(memberId));
    }

    /** 设置某抄表员的公司分配(全量覆盖)。 */
    @PostMapping("/{memberId}/companies")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Long>> setCompanies(@PathVariable Long memberId,
                                                @RequestBody AssignRequest req) {
        return ApiResponse.ok(readerService.setCompanies(memberId, req.companyIds()));
    }

    public record AssignRequest(@NotNull List<Long> companyIds) {
    }
}
