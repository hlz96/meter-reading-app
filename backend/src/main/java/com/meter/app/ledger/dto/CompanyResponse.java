package com.meter.app.ledger.dto;

import com.meter.app.ledger.entity.Company;

/** 公司响应。 */
public record CompanyResponse(
        Long id,
        String name,
        String contact,
        String phone,
        String remark
) {
    public static CompanyResponse from(Company c) {
        return new CompanyResponse(c.getId(), c.getName(), c.getContact(), c.getPhone(), c.getRemark());
    }
}
