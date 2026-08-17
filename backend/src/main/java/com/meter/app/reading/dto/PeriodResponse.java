package com.meter.app.reading.dto;

import com.meter.app.reading.entity.Period;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PeriodResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal elecPrice,
        BigDecimal waterPrice,
        Integer status
) {
    public static PeriodResponse from(Period p) {
        return new PeriodResponse(p.getId(), p.getName(), p.getStartDate(), p.getEndDate(),
                p.getElecPrice(), p.getWaterPrice(), p.getStatus());
    }
}
