package com.shelfy.note.dto;

import java.time.Instant;

public record NoteResponse(
        Long id,
        String content,
        Integer pageReference,
        Instant createdAt,
        Long bookId
) {
}
