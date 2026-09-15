package com.shelfy.review.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ReviewResponse(
        Long id,
        BigDecimal rating,
        String text,
        Instant createdAt,
        Long bookId,
        String authorName
) {
}
