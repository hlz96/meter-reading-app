package com.meter.app.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增/编辑公司请求。 */
public record CompanyRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String contact,
        @Size(max = 20) String phone,
        @Size(max = 255) String remark
) {
}
