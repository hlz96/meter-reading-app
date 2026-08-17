package com.meter.app.ledger.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.ledger.dto.CompanyRequest;
import com.meter.app.ledger.dto.CompanyResponse;
import com.meter.app.ledger.service.CompanyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    public ApiResponse<List<CompanyResponse>> list() {
        return ApiResponse.ok(companyService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CompanyResponse> create(@Valid @RequestBody CompanyRequest req) {
        return ApiResponse.ok(companyService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<CompanyResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody CompanyRequest req) {
        return ApiResponse.ok(companyService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        companyService.delete(id);
        return ApiResponse.ok();
    }
}
