package com.shelfy.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByBookIdOrderByCreatedAtDesc(Long bookId);

    Optional<Review> findByIdAndBookId(Long id, Long bookId);

    void deleteByBookId(Long bookId);

    List<Review> findByUserId(Long userId);
}
