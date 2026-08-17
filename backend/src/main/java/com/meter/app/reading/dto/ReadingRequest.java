package com.meter.app.reading.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 提交读数请求,对应 TRD 4.6。 */
public record ReadingRequest(
        @NotNull Long meterId,
        @NotNull Long periodId,
        @NotNull @PositiveOrZero BigDecimal currReading,
        @Size(max = 255) String photoUrl,
        @Size(max = 64) String clientUuid,   // 离线幂等键,可空
        @Size(max = 255) String remark
) {
}
