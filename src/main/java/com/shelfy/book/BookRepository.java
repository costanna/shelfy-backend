package com.shelfy.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIdAndOwnerId(Long id, Long ownerId);

    Optional<Book> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);

    List<Book> findByOwnerId(Long ownerId);

    List<Book> findByOwnerIdAndDeletedAtIsNull(Long ownerId);

    Page<Book> findByOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long ownerId, Pageable pageable);

    Page<Book> findByOwnerIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long ownerId, Pageable pageable);

    List<Book> findByDeletedAtBefore(java.time.Instant cutoff);

    List<Book> findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            List<Long> ownerIds, BookStatus status);

    List<Book> findByStatusAndStartedAtBeforeAndReminderSentAtIsNull(BookStatus status, LocalDate startedBefore);

    Page<Book> findByOwnerIdInAndStatusOrderByUpdatedAtDesc(List<Long> ownerIds, BookStatus status, Pageable pageable);

    @Query("select b from Book b where b.owner.id in :ownerIds and b.status = :status and b.deletedAt is null order by coalesce(b.statusChangedAt, b.updatedAt) desc")
    Page<Book> findFeedBooksByStatus(@Param("ownerIds") List<Long> ownerIds,
                                     @Param("status") BookStatus status,
                                     Pageable pageable);

    @Query("select b.id as id, b.title as title, b.author as author from Book b where b.owner.id = :ownerId and b.deletedAt is null order by b.title asc")
    List<BookKeyRow> findKeysByOwnerId(@Param("ownerId") Long ownerId);

    @Modifying
    @Query(value = "delete from book_categories where book_id in (select id from books where owner_id = :ownerId)", nativeQuery = true)
    void deleteJoinRowsByOwnerId(@Param("ownerId") Long ownerId);

    void deleteByOwnerId(Long ownerId);

    interface BookKeyRow {
        Long getId();

        String getTitle();

        String getAuthor();
    }

    @Query("select b.status as status, count(b) as total from Book b where b.owner.id = :ownerId and b.deletedAt is null group by b.status")
    List<BookStatusCount> countByOwnerIdGroupedByStatus(@Param("ownerId") Long ownerId);

    interface BookStatusCount {
        BookStatus getStatus();

        long getTotal();
    }
}
