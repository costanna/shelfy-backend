package com.shelfy.user.dto;

import java.util.List;

public record UserProfileResponse(
        Long id,
        String alias,
        String name,
        long followersCount,
        long followingCount,
        boolean followedByMe,
        boolean own,
        boolean visible,
        List<PublicBookResponse> books
) {
}
