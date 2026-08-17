package com.meter.app.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 周期用量/费用汇总(TRD §4.5/§5.5)。fee 为 null 表示该类型未定价。
 */
public record SummaryResponse(
        Long periodId,
        long pendingCount,   // 该周期待审读数数量,>0 提示管理员仍有未审
        List<Row> rows
) {
    public record Row(
            Long companyId,
            Integer type,        // 1电表 2水表
            BigDecimal usage,
            BigDecimal fee       // null=未定价
    ) {
    }
}
