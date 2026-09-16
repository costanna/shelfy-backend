package com.shelfy.mail;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.user.User;
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

    @Mock
    private BookRepository bookRepository;

    @Mock
    private EmailService emailService;

    @Captor
    private ArgumentCaptor<List<String>> titlesCaptor;

    private ReadingReminderService service;

    private User owner(boolean remindersEnabled) {
        return User.builder()
                .id(1L)
                .email("lector@shelfy.app")
                .password("hash")
                .name("Lectora")
                .remindersEnabled(remindersEnabled)
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
        service = new ReadingReminderService(bookRepository, emailService);
        User owner = owner(true);
        Book book = staleBook(owner, "Libro olvidado");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(book));

        int emailsSent = service.remindStaleReaders();

        assertThat(emailsSent).isEqualTo(1);
        verify(emailService).sendStaleReadingReminder(anyString(), anyString(), titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).containsExactly("Libro olvidado");
        assertThat(book.getReminderSentAt()).isNotNull();
    }

    @Test
    void doesNotSendWhenOwnerDisabledReminders() {
        service = new ReadingReminderService(bookRepository, emailService);
        User owner = owner(false);
        Book book = staleBook(owner, "Libro olvidado");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(book));

        int emailsSent = service.remindStaleReaders();

        assertThat(emailsSent).isZero();
        verify(emailService, never()).sendStaleReadingReminder(anyString(), anyString(), any());
        assertThat(book.getReminderSentAt()).isNull();
    }

    @Test
    void consolidatesMultipleStaleBooksForTheSameOwnerIntoOneEmail() {
        service = new ReadingReminderService(bookRepository, emailService);
        User owner = owner(true);
        Book bookA = staleBook(owner, "Primer libro");
        Book bookB = staleBook(owner, "Segundo libro");
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of(bookA, bookB));

        int emailsSent = service.remindStaleReaders();

        assertThat(emailsSent).isEqualTo(1);
        verify(emailService, times(1)).sendStaleReadingReminder(anyString(), anyString(), titlesCaptor.capture());
        assertThat(titlesCaptor.getValue()).containsExactlyInAnyOrder("Primer libro", "Segundo libro");
        assertThat(bookA.getReminderSentAt()).isNotNull();
        assertThat(bookB.getReminderSentAt()).isNotNull();
    }

    @Test
    void doesNothingWhenNoStaleBooksExist() {
        service = new ReadingReminderService(bookRepository, emailService);
        when(bookRepository.findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(any(), any()))
                .thenReturn(List.of());

        int emailsSent = service.remindStaleReaders();

        assertThat(emailsSent).isZero();
        verify(emailService, never()).sendStaleReadingReminder(anyString(), anyString(), any());
    }
}
