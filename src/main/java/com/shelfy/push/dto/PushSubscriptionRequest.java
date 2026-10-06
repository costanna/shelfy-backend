package com.shelfy.push.dto;

import jakarta.validation.constraints.NotBlank;

public record PushSubscriptionRequest(
        @NotBlank(message = "Falta el endpoint") String endpoint,
        @NotBlank(message = "Falta la clau p256dh") String p256dh,
        @NotBlank(message = "Falta la clau auth") String auth
) {
}
