package com.meter.app.report.dto;

import java.util.List;

/** 图表统一响应:type 标识维度,data 是对应的数据点列表。 */
public record ChartResponse(
        String type,
        List<?> data
) {
}
