package com.shelfy.mail;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.push.PushService;
import com.shelfy.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReadingReminderService {

    private static final int STALE_AFTER_DAYS = 30;

    private final BookRepository bookRepository;
    private final EmailService emailService;
    private final PushService pushService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sendStaleReadingReminders() {
        int currentHour = java.time.ZonedDateTime.now(ZoneId.systemDefault()).getHour();
        int sent = remindStaleReaders(currentHour);
        log.info("Recordatorios de lectura: {} usuarios avisados (hora {})", sent, currentHour);
    }

    @Transactional
    public int remindStaleReaders() {
        return remindStaleReaders(java.time.ZonedDateTime.now(ZoneId.systemDefault()).getHour());
    }

    @Transactional
    public int remindStaleReaders(int currentHour) {
        LocalDate cutoff = LocalDate.now().minusDays(STALE_AFTER_DAYS);
        List<Book> staleBooks = bookRepository
                .findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(BookStatus.READING, cutoff);

        Map<User, List<Book>> byOwner = staleBooks.stream()
                .filter(book -> book.getOwner().isRemindersEnabled()
                        && book.getOwner().getReminderHour() == currentHour)
                .collect(Collectors.groupingBy(Book::getOwner));

        Instant now = Instant.now();
        for (Map.Entry<User, List<Book>> entry : byOwner.entrySet()) {
            User owner = entry.getKey();
            List<Book> books = entry.getValue();
            List<String> titles = books.stream().map(Book::getTitle).toList();

            emailService.sendStaleReadingReminder(owner.getEmail(), owner.getName(), titles);
            pushService.sendToUser(owner.getId(), "¿Sigues con estos libros?",
                    String.join(", ", titles.stream().limit(3).toList()), "/books?status=READING");
            books.forEach(book -> book.setReminderSentAt(now));
        }

        return byOwner.size();
    }
}
