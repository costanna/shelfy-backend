package com.shelfy.stats.dto;

import java.util.List;

public record ReadingStatsResponse(
        long totalBooksRead,
        long totalBooks,
        long currentlyReading,
        List<BookReadingDuration> readingDurations,
        List<MonthlyReadCount> booksByMonth
) {
}
