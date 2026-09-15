package com.shelfy.book;

/** Filtros aceptados por GET /api/books (todos opcionales). */
public record BookFilter(
        BookStatus status,
        Long categoryId,
        String query
) {
}
