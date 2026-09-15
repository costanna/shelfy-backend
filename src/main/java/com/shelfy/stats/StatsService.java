package com.shelfy.stats;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.stats.dto.BookReadingDuration;
import com.shelfy.stats.dto.ReadingStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final BookRepository bookRepository;

    @Transactional(readOnly = true)
    public ReadingStatsResponse getStats(Long ownerId) {
        List<Book> books = bookRepository.findByOwnerId(ownerId);

        long totalBooksRead = books.stream()
                .filter(book -> book.getStatus() == BookStatus.READ)
                .count();

        List<BookReadingDuration> readingDurations = books.stream()
                .filter(book -> book.getStartedAt() != null && book.getFinishedAt() != null)
                .map(this::toDuration)
                .sorted(Comparator.comparing(BookReadingDuration::finishedAt).reversed())
                .toList();

        return new ReadingStatsResponse(totalBooksRead, books.size(), readingDurations);
    }

    /** Cuenta el día de inicio y el de fin como leídos (empezar y acabar el mismo día son "1 día"). */
    private BookReadingDuration toDuration(Book book) {
        long days = ChronoUnit.DAYS.between(book.getStartedAt(), book.getFinishedAt()) + 1;
        return new BookReadingDuration(
                book.getId(),
                book.getTitle(),
                book.getStartedAt(),
                book.getFinishedAt(),
                days
        );
    }
}
