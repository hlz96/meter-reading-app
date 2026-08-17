package com.meter.app.dataimport.dto;

import java.util.List;

/** 导入结果:成功行数、失败行数、错误明细。 */
public record ImportResult(
        int successCount,
        int failCount,
        List<String> errors
) {
}
