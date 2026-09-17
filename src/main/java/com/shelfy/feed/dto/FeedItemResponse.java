package com.shelfy.feed.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record FeedItemResponse(
        FeedItemType type,
        Long actorId,
        String actorAlias,
        String actorName,
        Long bookId,
        String bookTitle,
        String bookCoverUrl,
        BigDecimal rating,
        Instant occurredAt
) {
}
