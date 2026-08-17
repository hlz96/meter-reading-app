package com.meter.app.ledger.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** 新增/编辑表计请求。 */
public record MeterRequest(
        @NotNull Long companyId,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) @Max(2) Integer type,            // 1电表 2水表
        @NotNull @PositiveOrZero BigDecimal initialReading,
        @NotNull @Positive BigDecimal ratio,
        @Size(max = 100) String location,
        @Min(0) @Max(1) Integer status                    // 1启用 0停用,null 时默认启用
) {
}
