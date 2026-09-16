package com.shelfy.user.dto;

import jakarta.validation.constraints.NotBlank;

public record DeleteAccountRequest(
        @NotBlank(message = "Introduce tu contraseña para confirmar")
        String password
) {
}
