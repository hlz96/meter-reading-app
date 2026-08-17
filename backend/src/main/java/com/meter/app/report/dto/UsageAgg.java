package com.meter.app.report.dto;

import java.math.BigDecimal;

/**
 * 用量聚合投影:按公司+类型分组的用量合计。供 ReadingRepository JPQL 构造。
 */
public record UsageAgg(
        Long companyId,
        Integer type,
        BigDecimal usage
) {
}
