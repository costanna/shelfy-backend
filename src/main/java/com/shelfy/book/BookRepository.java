package com.shelfy.book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIdAndOwnerId(Long id, Long ownerId);

    List<Book> findByOwnerId(Long ownerId);

    List<Book> findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(BookStatus status, LocalDate startedBefore);
}
