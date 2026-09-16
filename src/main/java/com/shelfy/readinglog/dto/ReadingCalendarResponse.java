package com.shelfy.readinglog.dto;

import java.util.List;

public record ReadingCalendarResponse(
        int year,
        int month,
        List<ReadingDayResponse> days
) {
}
