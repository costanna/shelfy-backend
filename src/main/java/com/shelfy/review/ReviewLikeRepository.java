package com.shelfy.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {

    Optional<ReviewLike> findByReviewIdAndUserId(Long reviewId, Long userId);

    long countByReviewId(Long reviewId);

    List<ReviewLike> findByReviewIdInAndUserId(List<Long> reviewIds, Long userId);

    void deleteByReviewId(Long reviewId);

    void deleteByUserId(Long userId);
}
