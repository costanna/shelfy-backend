package com.shelfy.auth.dto;

public record RegisterResponse(
        String message,
        boolean requiresVerification
) {
}
