package com.shelfy.book.dto;

import java.time.LocalDate;

/**
 * Ambos campos nulos borra el rango (equivale a "eliminar" las fechas de
 * lectura del libro sin tocar el resto de sus datos). Sin restricciones de
 * Bean Validation a propósito: el único requisito —que finishedAt no sea
 * anterior a startedAt— solo aplica cuando ambos vienen rellenos, así que
 * se comprueba en el servicio en vez de forzar un validador de clase solo
 * para dos campos usados en un único sitio.
 */
public record UpdateReadingDatesRequest(
        LocalDate startedAt,
        LocalDate finishedAt
) {
}
