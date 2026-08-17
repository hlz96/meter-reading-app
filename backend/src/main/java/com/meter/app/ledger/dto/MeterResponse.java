package com.meter.app.ledger.dto;

import com.meter.app.ledger.entity.Meter;

import java.math.BigDecimal;

/** 表计响应。 */
public record MeterResponse(
        Long id,
        Long companyId,
        String name,
        Integer type,
        BigDecimal initialReading,
        BigDecimal ratio,
        String location,
        Integer status
) {
    public static MeterResponse from(Meter m) {
        return new MeterResponse(
                m.getId(), m.getCompanyId(), m.getName(), m.getType(),
                m.getInitialReading(), m.getRatio(), m.getLocation(), m.getStatus());
    }
}
