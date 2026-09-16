package com.shelfy.mail;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

@SpringBootTest
@ActiveProfiles("local")
class ReadingReminderServiceIntegrationTest {

    @Autowired
    private ReadingReminderService readingReminderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void schedulerEntryPointDoesNotThrowWhenThereIsARealStaleBook() {
        User owner = userRepository.save(User.builder()
                .email("stale-reader@shelfy.app")
                .password(passwordEncoder.encode("shelfy123"))
                .name("Stale Reader")
                .emailVerified(true)
                .remindersEnabled(true)
                .build());

        bookRepository.save(Book.builder()
                .owner(owner)
                .title("Libro abandonado")
                .status(BookStatus.READING)
                .startedAt(LocalDate.now().minusDays(40))
                .build());

        assertThatCode(() -> readingReminderService.sendStaleReadingReminders())
                .doesNotThrowAnyException();
    }
}
