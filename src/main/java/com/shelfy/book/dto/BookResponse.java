package com.shelfy.book.dto;

import com.shelfy.book.BookStatus;
import com.shelfy.category.dto.CategoryResponse;

import java.time.Instant;
import java.util.List;

public record BookResponse(
        Long id,
        String title,
        String author,
        String coverUrl,
        String isbn,
        String synopsis,
        Integer pageCount,
        BookStatus status,
        List<CategoryResponse> categories,
        Instant createdAt,
        Instant updatedAt
) {
}
