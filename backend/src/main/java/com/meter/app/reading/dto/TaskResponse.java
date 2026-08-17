package com.meter.app.reading.dto;

import java.util.List;

/** 待抄清单:某周期下各表计的应抄/已抄概览。 */
public record TaskResponse(
        Long periodId,
        int total,
        long doneCount,
        List<TaskItem> items
) {
}
