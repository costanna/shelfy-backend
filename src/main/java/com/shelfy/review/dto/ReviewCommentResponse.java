package com.shelfy.review.dto;

import java.time.Instant;

public record ReviewCommentResponse(
        Long id,
        String text,
        Instant createdAt,
        Long authorId,
        String authorName
) {
}
