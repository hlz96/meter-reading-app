package com.meter.app.reading.dto;

import java.math.BigDecimal;

/** 待抄清单单项:某表计在某周期的应抄/已抄状态。 */
public record TaskItem(
        Long meterId,
        String meterName,
        Long companyId,
        Integer type,
        boolean done,             // 是否已抄
        BigDecimal currReading,   // 已抄时的本期读数,未抄为 null
        BigDecimal usageAmount,   // 已抄时的用量,未抄为 null
        Integer auditStatus       // 已抄时的审核状态,未抄为 null
) {
}
