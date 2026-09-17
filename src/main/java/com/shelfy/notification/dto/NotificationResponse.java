package com.shelfy.notification.dto;

import com.shelfy.notification.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        NotificationType type,
        Long actorId,
        String actorAlias,
        String actorName,
        boolean read,
        Instant createdAt
) {
}
