package com.shelfy.stats.dto;

import java.util.List;

public record ReadingStatsResponse(
        long totalBooksRead,
        long totalBooks,
        List<BookReadingDuration> readingDurations
) {
}
