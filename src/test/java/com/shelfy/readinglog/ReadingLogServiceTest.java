package com.shelfy.readinglog;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.readinglog.dto.MarkReadingDayRequest;
import com.shelfy.readinglog.dto.ReadingCalendarResponse;
import com.shelfy.readinglog.dto.ReadingLogBookSummaryResponse;
import com.shelfy.readinglog.dto.ReadingStreakResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingLogServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long BOOK_ID = 10L;

    @Mock
    private ReadingLogRepository readingLogRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private UserRepository userRepository;

    private ReadingLogService service;

    @BeforeEach
    void setUp() {
        service = new ReadingLogService(readingLogRepository, bookRepository, userRepository);
    }

    private Book book(Long id, String title) {
        return Book.builder().id(id).title(title).build();
    }

    // --- mark / unmark ---

    @Test
    void mark_doesNothingWhenTheDayIsAlreadyMarkedForThatBook() {
        LocalDate date = LocalDate.now();
        when(readingLogRepository.findByOwnerIdAndBookIdAndDate(OWNER_ID, BOOK_ID, date))
                .thenReturn(Optional.of(new ReadingLog()));

        service.mark(OWNER_ID, new MarkReadingDayRequest(BOOK_ID, date));

        verify(readingLogRepository, never()).save(any());
        verify(readingLogRepository, never()).saveAndFlush(any());
    }

    @Test
    void mark_throwsWhenTheBookIsNotOwnedByTheCaller() {
        LocalDate date = LocalDate.now();
        when(readingLogRepository.findByOwnerIdAndBookIdAndDate(OWNER_ID, BOOK_ID, date))
                .thenReturn(Optional.empty());
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mark(OWNER_ID, new MarkReadingDayRequest(BOOK_ID, date)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void mark_savesANewEntryWhenNotAlreadyMarked() {
        LocalDate date = LocalDate.now();
        when(readingLogRepository.findByOwnerIdAndBookIdAndDate(OWNER_ID, BOOK_ID, date))
                .thenReturn(Optional.empty());
        when(bookRepository.findByIdAndOwnerIdAndDeletedAtIsNull(BOOK_ID, OWNER_ID)).thenReturn(Optional.of(book(BOOK_ID, "Dune")));
        when(userRepository.getReferenceById(OWNER_ID)).thenReturn(User.builder().id(OWNER_ID).build());

        service.mark(OWNER_ID, new MarkReadingDayRequest(BOOK_ID, date));

        verify(readingLogRepository).saveAndFlush(any(ReadingLog.class));
    }

    @Test
    void unmark_removesTheEntryWhenItExists() {
        LocalDate date = LocalDate.now();
        ReadingLog log = new ReadingLog();
        when(readingLogRepository.findByOwnerIdAndBookIdAndDate(OWNER_ID, BOOK_ID, date))
                .thenReturn(Optional.of(log));

        service.unmark(OWNER_ID, BOOK_ID, date);

        verify(readingLogRepository).delete(log);
    }

    // --- calendar ---

    @Test
    void calendar_rejectsAMonthOutsideOneToTwelve() {
        assertThatThrownBy(() -> service.calendar(OWNER_ID, 2026, 13))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calendar(OWNER_ID, 2026, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calendar_groupsMultipleBooksMarkedOnTheSameDay() {
        LocalDate day = LocalDate.of(2026, 3, 15);
        ReadingLog logA = ReadingLog.builder().book(book(1L, "Dune")).date(day).build();
        ReadingLog logB = ReadingLog.builder().book(book(2L, "1984")).date(day).build();
        when(readingLogRepository.findByOwnerIdAndDateBetweenOrderByDateAsc(
                OWNER_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
                .thenReturn(List.of(logA, logB));

        ReadingCalendarResponse calendar = service.calendar(OWNER_ID, 2026, 3);

        assertThat(calendar.days()).hasSize(1);
        assertThat(calendar.days().get(0).books()).hasSize(2);
    }

    // --- summary ---

    @Test
    void summary_groupsDatesByBookAndSortsByTitleIgnoringCase() {
        LocalDate d1 = LocalDate.of(2026, 1, 1);
        LocalDate d2 = LocalDate.of(2026, 1, 2);
        when(readingLogRepository.findAllWithBookByOwnerId(OWNER_ID)).thenReturn(List.of(
                ReadingLog.builder().book(book(1L, "zebra")).date(d1).build(),
                ReadingLog.builder().book(book(1L, "zebra")).date(d2).build(),
                ReadingLog.builder().book(book(2L, "Alpha")).date(d1).build()));

        List<ReadingLogBookSummaryResponse> summary = service.summary(OWNER_ID);

        assertThat(summary).extracting(ReadingLogBookSummaryResponse::title).containsExactly("Alpha", "zebra");
        assertThat(summary.get(1).dates()).containsExactly(d1, d2);
    }

    // --- streak ---

    @Test
    void streak_isZeroZeroWithNoHistory() {
        when(readingLogRepository.findDistinctDatesByOwnerId(OWNER_ID)).thenReturn(List.of());

        ReadingStreakResponse streak = service.streak(OWNER_ID);

        assertThat(streak.currentStreak()).isZero();
        assertThat(streak.longestStreak()).isZero();
    }

    @Test
    void streak_countsConsecutiveDaysEndingToday() {
        LocalDate today = LocalDate.now();
        lenient().when(readingLogRepository.findDistinctDatesByOwnerId(OWNER_ID))
                .thenReturn(List.of(today, today.minusDays(1), today.minusDays(2)));

        ReadingStreakResponse streak = service.streak(OWNER_ID);

        assertThat(streak.currentStreak()).isEqualTo(3);
        assertThat(streak.longestStreak()).isEqualTo(3);
    }

    @Test
    void streak_stillCountsAsCurrentWhenTheLastMarkedDayWasYesterday() {
        LocalDate today = LocalDate.now();
        when(readingLogRepository.findDistinctDatesByOwnerId(OWNER_ID))
                .thenReturn(List.of(today.minusDays(1), today.minusDays(2)));

        ReadingStreakResponse streak = service.streak(OWNER_ID);

        assertThat(streak.currentStreak()).isEqualTo(2);
    }

    @Test
    void streak_isZeroWhenTheLastMarkedDayWasAtLeastTwoDaysAgo() {
        LocalDate today = LocalDate.now();
        when(readingLogRepository.findDistinctDatesByOwnerId(OWNER_ID))
                .thenReturn(List.of(today.minusDays(5), today.minusDays(6), today.minusDays(7)));

        ReadingStreakResponse streak = service.streak(OWNER_ID);

        assertThat(streak.currentStreak()).isZero();
        assertThat(streak.longestStreak()).isEqualTo(3);
    }

    @Test
    void streak_currentStreakStopsAtTheFirstGapAndLongestLooksAtTheWholeHistory() {
        LocalDate today = LocalDate.now();
        // Racha actual: hoy y ayer (2). Luego un hueco (falta today-2), y otra racha
        // de dos días más atrás (today-3, today-4) que iguala pero no supera la larga.
        when(readingLogRepository.findDistinctDatesByOwnerId(OWNER_ID)).thenReturn(List.of(
                today, today.minusDays(1), today.minusDays(3), today.minusDays(4)));

        ReadingStreakResponse streak = service.streak(OWNER_ID);

        assertThat(streak.currentStreak()).isEqualTo(2);
        assertThat(streak.longestStreak()).isEqualTo(2);
    }
}
