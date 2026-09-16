package com.shelfy.goal.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReadingGoalRequest(
        @NotNull(message = "El objetivo es obligatorio")
        @Min(value = 1, message = "El objetivo debe ser mayor que 0")
        @Max(value = 1000, message = "El objetivo no puede superar los 1000 libros")
        Integer targetBooks
) {
}
