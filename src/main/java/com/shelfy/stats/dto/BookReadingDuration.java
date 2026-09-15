package com.shelfy.stats.dto;

import java.time.LocalDate;

public record BookReadingDuration(
        Long bookId,
        String title,
        LocalDate startedAt,
        LocalDate finishedAt,
        long daysReading
) {
}
