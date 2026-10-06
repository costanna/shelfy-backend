package com.shelfy.review;

import com.shelfy.book.Book;
import com.shelfy.book.BookService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowService;
import com.shelfy.notification.NotificationService;
import com.shelfy.review.dto.ReviewRequest;
import com.shelfy.review.dto.ReviewResponse;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewCommentRepository reviewCommentRepository;
    private final ReviewMapper reviewMapper;
    private final BookService bookService;
    private final UserService userService;
    private final FollowService followService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ReviewResponse> listByBook(Long userId, Long bookId) {
        bookService.findOwned(userId, bookId);

        List<Review> reviews = reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId);
        Set<Long> likedIds = reviewLikeRepository
                .findByReviewIdInAndUserId(reviews.stream().map(Review::getId).toList(), userId).stream()
                .map(like -> like.getReview().getId())
                .collect(Collectors.toSet());

        return reviews.stream()
                .map(review -> reviewMapper.toResponse(review,
                        reviewLikeRepository.countByReviewId(review.getId()),
                        likedIds.contains(review.getId())))
                .toList();
    }

    @Transactional
    public ReviewResponse create(Long userId, Long bookId, ReviewRequest request) {
        Book book = bookService.findOwned(userId, bookId);

        Review review = Review.builder()
                .book(book)
                .user(userService.getEntity(userId))
                .rating(request.rating())
                .text(request.text())
                .build();

        Review saved = reviewRepository.save(review);
        return reviewMapper.toResponse(saved, 0, false);
    }

    @Transactional
    public ReviewResponse update(Long userId, Long bookId, Long reviewId, ReviewRequest request) {
        Review review = findOwned(userId, bookId, reviewId);

        review.setRating(request.rating());
        review.setText(request.text());

        return reviewMapper.toResponse(review,
                reviewLikeRepository.countByReviewId(review.getId()),
                reviewLikeRepository.findByReviewIdAndUserId(reviewId, userId).isPresent());
    }

    @Transactional
    public void delete(Long userId, Long bookId, Long reviewId) {
        Review review = findOwned(userId, bookId, reviewId);
        reviewLikeRepository.deleteByReviewId(review.getId());
        reviewCommentRepository.deleteByReviewId(review.getId());
        reviewRepository.delete(review);
    }

    /**
     * Like sobre qualsevol ressenya visible: pròpia o d'algú que segueixes.
     * Idempotent; notifica l'autor la primera vegada (mai a un mateix).
     */
    @Transactional
    public ReviewResponse like(Long viewerId, Long reviewId) {
        Review review = findVisible(viewerId, reviewId);

        boolean created = reviewLikeRepository.findByReviewIdAndUserId(reviewId, viewerId)
                .map(existing -> false)
                .orElseGet(() -> {
                    reviewLikeRepository.save(ReviewLike.builder()
                            .review(review)
                            .user(userService.getEntity(viewerId))
                            .build());
                    return true;
                });

        if (created && !review.getUser().getId().equals(viewerId)) {
            notificationService.notifyReviewLiked(
                    review.getUser().getId(), viewerId,
                    userService.getEntity(viewerId).getName(), review.getBook().getTitle());
        }

        return reviewMapper.toResponse(review,
                reviewLikeRepository.countByReviewId(reviewId), true);
    }

    @Transactional
    public ReviewResponse unlike(Long viewerId, Long reviewId) {
        Review review = findVisible(viewerId, reviewId);

        reviewLikeRepository.findByReviewIdAndUserId(reviewId, viewerId)
                .ifPresent(reviewLikeRepository::delete);

        return reviewMapper.toResponse(review,
                reviewLikeRepository.countByReviewId(reviewId), false);
    }

    @Transactional(readOnly = true)
    public List<com.shelfy.review.dto.ReviewCommentResponse> comments(Long viewerId, Long reviewId) {
        Review review = findVisible(viewerId, reviewId);
        return reviewCommentRepository.findByReviewIdOrderByCreatedAtAsc(review.getId()).stream()
                .map(comment -> new com.shelfy.review.dto.ReviewCommentResponse(
                        comment.getId(), comment.getText(), comment.getCreatedAt(),
                        comment.getUser().getId(), comment.getUser().getName()))
                .toList();
    }

    @Transactional
    public com.shelfy.review.dto.ReviewCommentResponse comment(
            Long viewerId, Long reviewId, com.shelfy.review.dto.ReviewCommentRequest request) {
        Review review = findVisible(viewerId, reviewId);

        ReviewComment saved = reviewCommentRepository.save(ReviewComment.builder()
                .review(review)
                .user(userService.getEntity(viewerId))
                .text(request.text().trim())
                .build());

        if (!review.getUser().getId().equals(viewerId)) {
            notificationService.notifyReviewCommented(
                    review.getUser().getId(), viewerId,
                    userService.getEntity(viewerId).getName(), review.getBook().getTitle());
        }

        return new com.shelfy.review.dto.ReviewCommentResponse(
                saved.getId(), saved.getText(), saved.getCreatedAt(),
                viewerId, userService.getEntity(viewerId).getName());
    }

    @Transactional
    public void deleteComment(Long viewerId, Long reviewId, Long commentId) {
        findVisible(viewerId, reviewId);
        ReviewComment comment = reviewCommentRepository.findByIdAndReviewId(commentId, reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Comentario", commentId));
        if (!comment.getUser().getId().equals(viewerId)) {
            throw new ResourceNotFoundException("Comentario", commentId);
        }
        reviewCommentRepository.delete(comment);
    }

    private Review findVisible(Long viewerId, Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", reviewId));
        Long authorId = review.getUser().getId();
        if (!authorId.equals(viewerId) && !followService.isFollowing(viewerId, authorId)) {
            // 404 deliberat: les ressenyes alienes són privades fins a seguir l'autor.
            throw new ResourceNotFoundException("Reseña", reviewId);
        }
        return review;
    }

    private Review findOwned(Long userId, Long bookId, Long reviewId) {
        bookService.findOwned(userId, bookId);

        Review review = reviewRepository.findByIdAndBookId(reviewId, bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", reviewId));

        if (review.getUser() == null || !review.getUser().getId().equals(userId)) {
            // 404 deliberat (no 403): no revelar ni l'existència de dades alienes.
            throw new ResourceNotFoundException("Reseña", reviewId);
        }
        return review;
    }
}
