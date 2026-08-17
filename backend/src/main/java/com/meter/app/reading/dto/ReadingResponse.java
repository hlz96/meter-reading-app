package com.meter.app.reading.dto;

import com.meter.app.reading.entity.Reading;

import java.math.BigDecimal;

/** 读数响应,含计算出的用量与异常/审核标记,对应 TRD 4.6。 */
public record ReadingResponse(
        Long id,
        Long meterId,
        Long periodId,
        BigDecimal prevReading,
        BigDecimal currReading,
        BigDecimal usageAmount,
        Boolean isAbnormal,
        String abnormalType,
        Integer auditStatus,
        String auditRemark
) {
    public static ReadingResponse from(Reading r) {
        return new ReadingResponse(
                r.getId(), r.getMeterId(), r.getPeriodId(),
                r.getPrevReading(), r.getCurrReading(), r.getUsageAmount(),
                r.getIsAbnormal() == 1, r.getAbnormalType(), r.getAuditStatus(),
                r.getAuditRemark());
    }
}
