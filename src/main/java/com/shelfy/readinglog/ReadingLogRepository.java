package com.shelfy.readinglog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReadingLogRepository extends JpaRepository<ReadingLog, Long> {

    Optional<ReadingLog> findByOwnerIdAndBookIdAndDate(Long ownerId, Long bookId, LocalDate date);

    List<ReadingLog> findByOwnerIdAndDateBetweenOrderByDateAsc(Long ownerId, LocalDate from, LocalDate to);

    @Query("select distinct r.date from ReadingLog r where r.owner.id = :ownerId order by r.date desc")
    List<LocalDate> findDistinctDatesByOwnerId(@Param("ownerId") Long ownerId);
}
