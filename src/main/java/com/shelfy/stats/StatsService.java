package com.shelfy.stats;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.stats.dto.BookReadingDuration;
import com.shelfy.stats.dto.MonthlyReadCount;
import com.shelfy.stats.dto.ReadingStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

        long currentlyReading = books.stream()
                .filter(book -> book.getStatus() == BookStatus.READING)
                .count();

        List<BookReadingDuration> readingDurations = books.stream()
                .filter(book -> book.getStartedAt() != null && book.getFinishedAt() != null)
                .map(this::toDuration)
                .sorted(Comparator.comparing(BookReadingDuration::finishedAt).reversed())
                .toList();

        List<MonthlyReadCount> booksByMonth = booksByMonth(books);

        return new ReadingStatsResponse(totalBooksRead, books.size(), currentlyReading, readingDurations, booksByMonth);
    }

    private List<MonthlyReadCount> booksByMonth(List<Book> books) {
        Map<YearMonth, Long> counts = books.stream()
                .filter(book -> book.getStatus() == BookStatus.READ && book.getFinishedAt() != null)
                .collect(Collectors.groupingBy(
                        book -> YearMonth.from(book.getFinishedAt()),
                        Collectors.counting()
                ));

        return counts.entrySet().stream()
                .map(entry -> new MonthlyReadCount(
                        entry.getKey().getYear(),
                        entry.getKey().getMonthValue(),
                        entry.getValue()
                ))
                .sorted(Comparator.comparing((MonthlyReadCount m) -> YearMonth.of(m.year(), m.month()))
                        .reversed())
                .toList();
    }

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
