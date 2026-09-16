package com.shelfy.goal.dto;

public record ReadingGoalResponse(
        int year,
        Integer targetBooks,
        long booksRead
) {
}
