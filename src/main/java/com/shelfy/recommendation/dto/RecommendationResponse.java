package com.shelfy.recommendation.dto;

import java.util.List;

public record RecommendationResponse(
        String basedOnAuthor,
        List<RecommendationItemResponse> items
) {
}
