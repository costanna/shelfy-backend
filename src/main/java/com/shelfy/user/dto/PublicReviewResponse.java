package com.shelfy.user.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PublicReviewResponse(
        Long id,
        BigDecimal rating,
        String text,
        Instant createdAt
) {
}
