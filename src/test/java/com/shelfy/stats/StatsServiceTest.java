package com.shelfy.stats;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.book.ReadEvent;
import com.shelfy.book.ReadEventRepository;
import com.shelfy.stats.dto.BookReadingDuration;
import com.shelfy.stats.dto.MonthlyReadCount;
import com.shelfy.stats.dto.ReadingStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatsServiceTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReadEventRepository readEventRepository;

    private StatsService service;

    @BeforeEach
    void setUp() {
        service = new StatsService(bookRepository, readEventRepository);
        when(readEventRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of());
    }

    private Book book(Long id, BookStatus status, LocalDate startedAt, LocalDate finishedAt) {
        return Book.builder().id(id).title("Libro " + id).status(status)
                .startedAt(startedAt).finishedAt(finishedAt).build();
    }

    @Test
    void getStats_ofAnEmptyLibraryReturnsAllZeros() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());

        ReadingStatsResponse stats = service.getStats(OWNER_ID);

        assertThat(stats.totalBooksRead()).isZero();
        assertThat(stats.totalBooks()).isZero();
        assertThat(stats.currentlyReading()).isZero();
        assertThat(stats.readingDurations()).isEmpty();
        assertThat(stats.booksByMonth()).isEmpty();
    }

    @Test
    void getStats_countsReadAndReadingBooksSeparatelyFromTheTotal() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                book(1L, BookStatus.READ, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)),
                book(2L, BookStatus.READING, LocalDate.of(2026, 2, 1), null),
                book(3L, BookStatus.WANT_TO_READ, null, null)));

        ReadingStatsResponse stats = service.getStats(OWNER_ID);

        assertThat(stats.totalBooksRead()).isEqualTo(1);
        assertThat(stats.currentlyReading()).isEqualTo(1);
        assertThat(stats.totalBooks()).isEqualTo(3);
    }

    @Test
    void getStats_computesInclusiveDayCountForACompletedBook() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                book(1L, BookStatus.READ, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10))));

        List<BookReadingDuration> durations = service.getStats(OWNER_ID).readingDurations();

        assertThat(durations).hasSize(1);
        // Del 1 al 10 de enero ambos inclusive son 10 días, no 9.
        assertThat(durations.get(0).daysReading()).isEqualTo(10);
        assertThat(durations.get(0).current()).isTrue();
    }

    @Test
    void getStats_ignoresAReadBookWithoutAFinishDate() {
        // Un libro "READ" sin finishedAt no debería poder pasar (la UI no lo permite),
        // pero si pasara, no debe aparecer como una lectura completada.
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                book(1L, BookStatus.READ, LocalDate.of(2026, 1, 1), null)));

        ReadingStatsResponse stats = service.getStats(OWNER_ID);

        assertThat(stats.readingDurations()).isEmpty();
        assertThat(stats.booksByMonth()).isEmpty();
    }

    @Test
    void getStats_includesPastRereadsFromReadEventsAsNotCurrent() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());
        Book book = book(1L, BookStatus.READ, null, null);
        ReadEvent event = ReadEvent.builder().book(book)
                .startedAt(LocalDate.of(2025, 6, 1)).finishedAt(LocalDate.of(2025, 6, 15)).build();
        when(readEventRepository.findByOwnerId(OWNER_ID)).thenReturn(List.of(event));

        List<BookReadingDuration> durations = service.getStats(OWNER_ID).readingDurations();

        assertThat(durations).hasSize(1);
        assertThat(durations.get(0).current()).isFalse();
        assertThat(durations.get(0).daysReading()).isEqualTo(15);
    }

    @Test
    void getStats_sortsReadingDurationsByMostRecentlyFinishedFirst() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                book(1L, BookStatus.READ, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 5)),
                book(2L, BookStatus.READ, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5))));

        List<BookReadingDuration> durations = service.getStats(OWNER_ID).readingDurations();

        assertThat(durations).extracting(BookReadingDuration::bookId).containsExactly(2L, 1L);
    }

    @Test
    void getStats_groupsCompletionsByMonthAndSortsMostRecentFirst() {
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(
                book(1L, BookStatus.READ, LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 10)),
                book(2L, BookStatus.READ, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 1, 20)),
                book(3L, BookStatus.READ, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5))));

        List<MonthlyReadCount> byMonth = service.getStats(OWNER_ID).booksByMonth();

        assertThat(byMonth).containsExactly(
                new MonthlyReadCount(2026, 3, 1),
                new MonthlyReadCount(2026, 1, 2));
    }
}
