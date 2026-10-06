package com.shelfy.mail;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.push.PushService;
import com.shelfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadingReminderServiceTest {

    private static final int HOUR = 9;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PushService pushService;

    @Captor
    private ArgumentCaptor<List<String>> titlesCaptor;

    private ReadingReminderService service;

    @BeforeEach
    void setUp() {
        service = new ReadingReminderService(bookRepository, emailService, pushService);
    }

    private User owner(boolean remindersEnabled) {
        return User.builder()
                .id(1L)
                .email("lector@shelfy.app")
                .password("hash")
                .name("Lectora")
                .remindersEnabled(remindersEnabled)
                .reminderHour(HOUR)
                .build();
    }

    private Book staleBook(User owner, String title) {
        return Book.builder()
                .id(1L)
                .title(title)
                .owner(owner)
                .status(BookStatus.READING)
                .startedAt(LocalDate.now().minusDays(40))
                .build();
    }

    @Test
    void sendsOneReminderAndMarksBookWhenOwnerHasRemindersEnabled() {
        User owner = owner(true);
        Book book = staleBook(owner, "Libro olvidado");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(book));

        int emailsSent = service.remindStaleReaders(HOUR);

        assertThat(emailsSent).isEqualTo(1);
        verify(emailService).sendStaleReadingReminder(anyString(), anyString(), titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).containsExactly("Libro olvidado");
        assertThat(book.getReminderSentAt()).isNotNull();
    }

    @Test
    void doesNotSendWhenOwnerDisabledReminders() {
        User owner = owner(false);
        Book book = staleBook(owner, "Libro olvidado");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(book));

        int emailsSent = service.remindStaleReaders(HOUR);

        assertThat(emailsSent).isZero();
        verify(emailService, never()).sendStaleReadingReminder(anyString(), anyString(), any());
        assertThat(book.getReminderSentAt()).isNull();
    }

    @Test
    void doesNotSendWhenCurrentHourDoesNotMatchOwnerPreference() {
        User owner = owner(true);
        Book book = staleBook(owner, "Libro olvidado");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(book));

        int emailsSent = service.remindStaleReaders((HOUR + 3) % 24);

        assertThat(emailsSent).isZero();
        verify(emailService, never()).sendStaleReadingReminder(anyString(), anyString(), any());
        assertThat(book.getReminderSentAt()).isNull();
    }

    @Test
    void consolidatesMultipleStaleBooksForTheSameOwnerIntoOneEmail() {
        User owner = owner(true);
        Book bookA = staleBook(owner, "Primer libro");
        Book bookB = staleBook(owner, "Segundo libro");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(bookA, bookB));

        int emailsSent = service.remindStaleReaders(HOUR);

        assertThat(emailsSent).isEqualTo(1);
        verify(emailService, times(1)).sendStaleReadingReminder(anyString(), anyString(), titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).containsExactlyInAnyOrder("Primer libro", "Segundo libro");
        assertThat(bookA.getReminderSentAt()).isNotNull();
        assertThat(bookB.getReminderSentAt()).isNotNull();
    }

    @Test
    void doesNothingWhenNoStaleBooksExist() {
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of());

        int emailsSent = service.remindStaleReaders(HOUR);

        assertThat(emailsSent).isZero();
        verify(emailService, never()).sendStaleReadingReminder(anyString(), anyString(), any());
    }
}
