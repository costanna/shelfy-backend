package com.shelfy.readinglog;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.readinglog.dto.MarkReadingDayRequest;
import com.shelfy.readinglog.dto.ReadingCalendarResponse;
import com.shelfy.readinglog.dto.ReadingDayResponse;
import com.shelfy.readinglog.dto.ReadingLogBookResponse;
import com.shelfy.readinglog.dto.ReadingStreakResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReadingLogService {

    private final ReadingLogRepository readingLogRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    /**
     * Idempotente a propósito: el calendario del frontend llama a esto al
     * pulsar un libro en el selector de un día, sin comprobar antes si ya
     * estaba marcado — más simple que tener que sincronizar estado local
     * con un 409. Si ya existía, no hace nada.
     */
    @Transactional
    public void mark(Long ownerId, MarkReadingDayRequest request) {
        if (readingLogRepository.findByOwnerIdAndBookIdAndDate(ownerId, request.bookId(), request.date()).isPresent()) {
            return;
        }

        Book book = bookRepository.findByIdAndOwnerId(request.bookId(), ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Libro", request.bookId()));
        User owner = userRepository.getReferenceById(ownerId);

        readingLogRepository.save(ReadingLog.builder()
                .owner(owner)
                .book(book)
                .date(request.date())
                .build());
    }

    @Transactional
    public void unmark(Long ownerId, Long bookId, LocalDate date) {
        readingLogRepository.findByOwnerIdAndBookIdAndDate(ownerId, bookId, date)
                .ifPresent(readingLogRepository::delete);
    }

    @Transactional(readOnly = true)
    public ReadingCalendarResponse calendar(Long ownerId, int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("El mes debe estar entre 1 y 12");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        List<ReadingLog> logs = readingLogRepository.findByOwnerIdAndDateBetweenOrderByDateAsc(
                ownerId, yearMonth.atDay(1), yearMonth.atEndOfMonth());

        Map<LocalDate, List<ReadingLogBookResponse>> byDate = logs.stream()
                .collect(Collectors.groupingBy(
                        ReadingLog::getDate,
                        LinkedHashMap::new,
                        Collectors.mapping(this::toBookResponse, Collectors.toList())
                ));

        List<ReadingDayResponse> days = byDate.entrySet().stream()
                .map(entry -> new ReadingDayResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ReadingDayResponse::date))
                .toList();

        return new ReadingCalendarResponse(year, month, days);
    }

    @Transactional(readOnly = true)
    public ReadingStreakResponse streak(Long ownerId) {
        List<LocalDate> datesDesc = readingLogRepository.findDistinctDatesByOwnerId(ownerId);
        return new ReadingStreakResponse(currentStreak(datesDesc), longestStreak(datesDesc));
    }

    /**
     * La racha sigue "viva" mientras el último día marcado sea hoy o ayer
     * (para no penalizar a quien aún no ha marcado el día de hoy): si el
     * hueco es de dos días o más, la racha está rota y vale 0.
     */
    private int currentStreak(List<LocalDate> datesDesc) {
        if (datesDesc.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        LocalDate mostRecent = datesDesc.get(0);
        if (mostRecent.isBefore(today.minusDays(1))) {
            return 0;
        }

        int streak = 1;
        LocalDate expected = mostRecent.minusDays(1);
        for (int i = 1; i < datesDesc.size(); i++) {
            LocalDate current = datesDesc.get(i);
            if (current.equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (current.isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    private int longestStreak(List<LocalDate> datesDesc) {
        if (datesDesc.isEmpty()) {
            return 0;
        }

        List<LocalDate> sorted = datesDesc.stream().sorted().toList();
        int longest = 1;
        int current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).equals(sorted.get(i - 1).plusDays(1))) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    private ReadingLogBookResponse toBookResponse(ReadingLog log) {
        Book book = log.getBook();
        return new ReadingLogBookResponse(book.getId(), book.getTitle(), book.getCoverUrl());
    }
}
