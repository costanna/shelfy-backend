package com.shelfy.readinglog.dto;

import java.time.LocalDate;
import java.util.List;

public record ReadingLogBookSummaryResponse(
        Long bookId,
        String title,
        List<LocalDate> dates
) {
}
