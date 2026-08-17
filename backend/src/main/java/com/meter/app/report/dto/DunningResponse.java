package com.meter.app.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 催缴数据(TRD §4.5):按公司归集电费+水费+合计。只计已通过审核的读数。
 * 费用含未定价情况时,该项为 null,合计只累加已定价部分。
 */
public record DunningResponse(
        Long periodId,
        long pendingCount,
        List<Row> rows
) {
    public record Row(
            Long companyId,
            BigDecimal elecUsage,
            BigDecimal elecFee,   // null=电价未定
            BigDecimal waterUsage,
            BigDecimal waterFee,  // null=水价未定
            BigDecimal totalFee   // 已定价部分之和
    ) {
    }
}
