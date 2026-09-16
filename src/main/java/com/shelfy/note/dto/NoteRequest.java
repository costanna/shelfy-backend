package com.shelfy.note.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NoteRequest(
        @NotBlank(message = "La nota no puede estar vacía")
        @Size(max = 2000, message = "La nota no puede superar los 2000 caracteres")
        String content,

        @Min(value = 1, message = "La página debe ser mayor que 0")
        Integer pageReference
) {
}
