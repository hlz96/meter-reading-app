package com.meter.app.report.dto;

import java.math.BigDecimal;

/** 占比图数据项:某公司的用量与占比。 */
public record RatioSlice(
        Long companyId,
        BigDecimal value,
        BigDecimal percent
) {
}
