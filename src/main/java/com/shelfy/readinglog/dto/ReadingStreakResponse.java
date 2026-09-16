package com.shelfy.readinglog.dto;

public record ReadingStreakResponse(
        int currentStreak,
        int longestStreak
) {
}
