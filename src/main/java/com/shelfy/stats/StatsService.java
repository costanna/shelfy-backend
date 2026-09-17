package com.shelfy.stats;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.book.ReadEvent;
import com.shelfy.book.ReadEventRepository;
import com.shelfy.stats.dto.BookReadingDuration;
import com.shelfy.stats.dto.MonthlyReadCount;
import com.shelfy.stats.dto.ReadingStatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final BookRepository bookRepository;
    private final ReadEventRepository readEventRepository;

    @Transactional(readOnly = true)
    public ReadingStatsResponse getStats(Long ownerId) {
        List<Book> books = bookRepository.findByOwnerId(ownerId);
        List<ReadEvent> pastReads = readEventRepository.findByOwnerId(ownerId);

        long totalBooksRead = books.stream()
                .filter(book -> book.getStatus() == BookStatus.READ)
                .count();

        long currentlyReading = books.stream()
                .filter(book -> book.getStatus() == BookStatus.READING)
                .count();

        List<BookReadingDuration> completions = new ArrayList<>();

        for (Book book : books) {
            if (book.getStatus() == BookStatus.READ && book.getFinishedAt() != null) {
                completions.add(toDuration(book.getId(), book.getTitle(), book.getStartedAt(), book.getFinishedAt(), true));
            }
        }
        for (ReadEvent event : pastReads) {
            completions.add(toDuration(
                    event.getBook().getId(), event.getBook().getTitle(),
                    event.getStartedAt(), event.getFinishedAt(), false));
        }

        List<BookReadingDuration> readingDurations = completions.stream()
                .filter(completion -> completion.startedAt() != null)
                .sorted(Comparator.comparing(BookReadingDuration::finishedAt).reversed())
                .toList();

        List<MonthlyReadCount> booksByMonth = booksByMonth(completions);

        return new ReadingStatsResponse(totalBooksRead, books.size(), currentlyReading, readingDurations, booksByMonth);
    }

    private List<MonthlyReadCount> booksByMonth(List<BookReadingDuration> completions) {
        Map<YearMonth, Long> counts = completions.stream()
                .collect(Collectors.groupingBy(
                        completion -> YearMonth.from(completion.finishedAt()),
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

    private BookReadingDuration toDuration(Long bookId, String title, LocalDate startedAt, LocalDate finishedAt,
                                            boolean current) {
        long days = startedAt != null ? ChronoUnit.DAYS.between(startedAt, finishedAt) + 1 : 0;
        return new BookReadingDuration(bookId, title, startedAt, finishedAt, days, current);
    }
}
