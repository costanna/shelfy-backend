package com.shelfy.book.dto;

import java.time.LocalDate;

public record ReadEventResponse(
        LocalDate startedAt,
        LocalDate finishedAt
) {
}
