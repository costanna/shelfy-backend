package com.shelfy.book;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReadEventRepository extends JpaRepository<ReadEvent, Long> {

    List<ReadEvent> findByBookIdOrderByFinishedAtDesc(Long bookId);

    List<ReadEvent> findByBookIdInOrderByFinishedAtDesc(List<Long> bookIds);

    void deleteByBookId(Long bookId);

    @Query("select r from ReadEvent r join fetch r.book b where b.owner.id = :ownerId")
    List<ReadEvent> findByOwnerId(@Param("ownerId") Long ownerId);
}
