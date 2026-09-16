package com.shelfy.readinglog.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record MarkReadingDayRequest(
        @NotNull(message = "Falta el libro") Long bookId,
        @NotNull(message = "Falta la fecha")
        @PastOrPresent(message = "No puedes marcar un día futuro")
        LocalDate date
) {
}
