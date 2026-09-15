package com.shelfy.review.dto;

import java.time.Instant;

public record ReviewResponse(
        Long id,
        Integer rating,
        String text,
        Instant createdAt,
        Long bookId,
        String authorName
) {
}
