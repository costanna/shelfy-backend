package com.shelfy.book.dto;

import com.shelfy.book.BookFormat;
import com.shelfy.book.BookStatus;
import com.shelfy.category.dto.CategoryResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BookResponse(
        Long id,
        String title,
        String author,
        String coverUrl,
        String isbn,
        String synopsis,
        Integer pageCount,
        Integer currentPage,
        String series,
        Integer seriesPosition,
        BookFormat format,
        BookStatus status,
        LocalDate startedAt,
        LocalDate finishedAt,
        List<CategoryResponse> categories,
        Instant createdAt,
        Instant updatedAt
) {
}
