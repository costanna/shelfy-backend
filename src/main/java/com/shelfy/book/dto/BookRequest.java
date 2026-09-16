package com.shelfy.book.dto;

import com.shelfy.book.BookStatus;
import com.shelfy.book.validation.ValidDateRange;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

@ValidDateRange
public record BookRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede superar los 255 caracteres")
        String title,

        @Size(max = 255, message = "El autor no puede superar los 255 caracteres")
        String author,

        @Size(max = 1000, message = "La URL de portada es demasiado larga")
        String coverUrl,

        @Size(max = 20, message = "El ISBN no puede superar los 20 caracteres")
        String isbn,

        @Size(max = 5000, message = "La sinopsis no puede superar los 5000 caracteres")
        String synopsis,

        @Min(value = 1, message = "El número de páginas debe ser mayor que 0")
        Integer pageCount,

        @NotNull(message = "El estado es obligatorio")
        BookStatus status,

        LocalDate startedAt,

        LocalDate finishedAt,

        Set<Long> categoryIds
) {
}
