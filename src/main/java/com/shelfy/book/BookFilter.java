package com.shelfy.book;

public record BookFilter(
        BookStatus status,
        Long categoryId,
        String query
) {
}
