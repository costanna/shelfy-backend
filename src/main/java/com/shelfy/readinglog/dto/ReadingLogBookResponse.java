package com.shelfy.readinglog.dto;

public record ReadingLogBookResponse(
        Long id,
        String title,
        String coverUrl
) {
}
