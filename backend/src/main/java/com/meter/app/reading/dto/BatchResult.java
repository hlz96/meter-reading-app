package com.meter.app.reading.dto;

import java.util.List;

/** 批量提交结果:成功/失败条数与错误明细(带条目序号)。 */
public record BatchResult(
        int successCount,
        int failCount,
        List<String> errors
) {
}
