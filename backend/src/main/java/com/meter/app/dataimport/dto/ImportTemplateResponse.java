package com.meter.app.dataimport.dto;

import com.meter.app.dataimport.entity.ImportTemplate;

public record ImportTemplateResponse(
        Long id,
        String name,
        String fieldMapping,
        Integer isDefault
) {
    public static ImportTemplateResponse from(ImportTemplate t) {
        return new ImportTemplateResponse(t.getId(), t.getName(), t.getFieldMapping(), t.getIsDefault());
    }
}
