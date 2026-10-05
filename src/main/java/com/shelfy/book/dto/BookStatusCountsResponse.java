package com.shelfy.book.dto;

public record BookStatusCountsResponse(
        long wantToRead,
        long reading,
        long read,
        long wantToBuy
) {
    public long total() {
        return wantToRead + reading + read + wantToBuy;
    }
}
