package com.shelfy.readinglog.dto;

import java.time.LocalDate;
import java.util.List;

public record ReadingDayResponse(
        LocalDate date,
        List<ReadingLogBookResponse> books
) {
}
