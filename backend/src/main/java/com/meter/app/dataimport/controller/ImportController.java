package com.meter.app.dataimport.controller;

import com.meter.app.common.ApiResponse;
import com.meter.app.dataimport.dto.ImportResult;
import com.meter.app.dataimport.dto.ImportTemplateResponse;
import com.meter.app.dataimport.service.ImportService;
import com.meter.app.export.ExcelUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 可配置导入接口(TRD §6.4)。
 */
@RestController
@RequestMapping("/api/v1/import")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    /** 模板列表(管理员)。 */
    @GetMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ImportTemplateResponse>> listTemplates() {
        return ApiResponse.ok(importService.listTemplates());
    }

    /** 新建模板(管理员)。 */
    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<ImportTemplateResponse> createTemplate(@RequestBody TemplateRequest req) {
        return ApiResponse.ok(importService.createTemplate(req.name(), req.fieldMapping()));
    }

    /** 下载样例 Excel(管理员)。按模板字段映射生成只含表头的空表。 */
    @GetMapping("/templates/{id}/sample")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> sample(@PathVariable Long id) {
        return ExcelUtil.asAttachment(importService.buildSampleHead(id), "导入样例");
    }

    /** 导入读数(管理员或抄表员)。multipart 上传 Excel。 */
    @PostMapping("/readings")
    @PreAuthorize("hasAnyRole('ADMIN','READER')")
    public ApiResponse<ImportResult> importReadings(@RequestParam Long templateId,
                                                    @RequestParam Long periodId,
                                                    @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importReadings(templateId, periodId, file));
    }

    public record TemplateRequest(@NotBlank String name, @NotNull Map<String, String> fieldMapping) {
    }
}
