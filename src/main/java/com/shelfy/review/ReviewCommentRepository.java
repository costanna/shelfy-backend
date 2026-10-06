package com.shelfy.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewCommentRepository extends JpaRepository<ReviewComment, Long> {

    List<ReviewComment> findByReviewIdOrderByCreatedAtAsc(Long reviewId);

    Optional<ReviewComment> findByIdAndReviewId(Long id, Long reviewId);

    void deleteByReviewId(Long reviewId);

    void deleteByUserId(Long userId);
}
