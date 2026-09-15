package com.shelfy.review.dto;

import com.shelfy.review.validation.HalfStep;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ReviewRequest(
        @NotNull(message = "La puntuación es obligatoria")
        @DecimalMin(value = "0.5", message = "La puntuación mínima es 0.5")
        @DecimalMax(value = "5.0", message = "La puntuación máxima es 5")
        @HalfStep(message = "La puntuación debe ir en pasos de 0.5")
        BigDecimal rating,

        @Size(max = 5000, message = "La reseña no puede superar los 5000 caracteres")
        String text
) {
}
