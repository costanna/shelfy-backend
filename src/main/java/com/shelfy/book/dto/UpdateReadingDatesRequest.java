package com.shelfy.book.dto;

import java.time.LocalDate;

public record UpdateReadingDatesRequest(
        LocalDate startedAt,
        LocalDate finishedAt
) {
}
