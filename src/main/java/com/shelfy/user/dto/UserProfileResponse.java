package com.shelfy.user.dto;

import java.time.Instant;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String alias,
        String name,
        Instant avatarUpdatedAt,
        long followersCount,
        long followingCount,
        boolean followedByMe,
        boolean own,
        boolean visible,
        List<PublicBookResponse> books
) {
}
