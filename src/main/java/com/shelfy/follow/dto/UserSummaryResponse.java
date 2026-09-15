package com.shelfy.follow.dto;

public record UserSummaryResponse(
        Long id,
        String alias,
        String name,
        long followersCount,
        boolean followedByMe
) {
}
