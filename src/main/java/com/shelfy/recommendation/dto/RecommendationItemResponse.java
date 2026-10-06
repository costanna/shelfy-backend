package com.shelfy.recommendation.dto;

public record RecommendationItemResponse(
        String title,
        String author,
        String coverUrl,
        long readerCount
) {
}
