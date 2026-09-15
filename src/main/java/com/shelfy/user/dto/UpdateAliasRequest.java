package com.shelfy.user.dto;

import com.shelfy.user.validation.NoProfanity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateAliasRequest(
        @NotBlank(message = "El alias es obligatorio")
        @Size(min = 3, max = 24, message = "El alias debe tener entre 3 y 24 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9_]+$",
                message = "El alias solo puede tener letras, números y guion bajo"
        )
        @NoProfanity
        String alias
) {
}
