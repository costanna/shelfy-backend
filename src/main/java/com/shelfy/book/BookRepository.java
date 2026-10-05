package com.shelfy.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIdAndOwnerId(Long id, Long ownerId);

    List<Book> findByOwnerId(Long ownerId);

    List<Book> findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(BookStatus status, LocalDate startedBefore);

    Page<Book> findByOwnerIdInAndStatusOrderByUpdatedAtDesc(List<Long> ownerIds, BookStatus status, Pageable pageable);

    @Query("select b.status as status, count(b) as total from Book b where b.owner.id = :ownerId group by b.status")
    List<BookStatusCount> countByOwnerIdGroupedByStatus(@Param("ownerId") Long ownerId);

    interface BookStatusCount {
        BookStatus getStatus();

        long getTotal();
    }
}
