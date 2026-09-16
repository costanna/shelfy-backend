package com.shelfy.follow.dto;

import java.time.Instant;

public record UserSummaryResponse(
        Long id,
        String alias,
        String name,
        Instant avatarUpdatedAt,
        long followersCount,
        boolean followedByMe
) {
}
