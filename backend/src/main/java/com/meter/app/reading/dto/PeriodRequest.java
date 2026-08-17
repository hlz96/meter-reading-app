package com.meter.app.reading.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 新增/编辑周期请求。费率可空(未定价);起止日期自定义。 */
public record PeriodRequest(
        @NotBlank @Size(max = 50) String name,
        LocalDate startDate,
        LocalDate endDate,
        @PositiveOrZero BigDecimal elecPrice,
        @PositiveOrZero BigDecimal waterPrice
) {
}
