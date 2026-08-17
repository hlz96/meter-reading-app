package com.meter.app.report.dto;

import java.math.BigDecimal;

/** 趋势图数据点:某周期的用量合计。 */
public record TrendPoint(
        String periodName,
        BigDecimal usage
) {
}
