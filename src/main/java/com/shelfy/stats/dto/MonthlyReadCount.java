package com.shelfy.stats.dto;

public record MonthlyReadCount(
        int year,
        int month,
        long count
) {
}
