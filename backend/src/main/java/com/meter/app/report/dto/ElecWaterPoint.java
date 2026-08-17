package com.meter.app.report.dto;

import java.math.BigDecimal;

/** 电水对比数据项:某公司的电量与水量。 */
public record ElecWaterPoint(
        Long companyId,
        BigDecimal elecUsage,
        BigDecimal waterUsage
) {
}
