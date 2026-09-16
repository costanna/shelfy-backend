package com.shelfy.book.dto;

import java.util.List;

public record BookImportResult(
        int imported,
        int skipped,
        List<String> messages
) {
}
