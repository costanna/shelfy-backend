package com.shelfy.user.dto;

import com.shelfy.book.BookStatus;
import com.shelfy.category.dto.CategoryResponse;

import java.util.List;

public record PublicBookResponse(
        Long id,
        String title,
        String author,
        String coverUrl,
        String synopsis,
        Integer pageCount,
        BookStatus status,
        List<CategoryResponse> categories,
        List<PublicReviewResponse> reviews
) {
}
