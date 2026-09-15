package com.shelfy.auth.dto;

import com.shelfy.user.dto.UserResponse;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        UserResponse user
) {

    public static AuthResponse of(String token, long expiresIn, UserResponse user) {
        return new AuthResponse(token, "Bearer", expiresIn, user);
    }
}
