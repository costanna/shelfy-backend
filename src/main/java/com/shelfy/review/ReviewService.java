package com.shelfy.review;

import com.shelfy.book.Book;
import com.shelfy.book.BookService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.review.dto.ReviewRequest;
import com.shelfy.review.dto.ReviewResponse;
import com.shelfy.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewMapper reviewMapper;
    private final BookService bookService;
    private final UserService userService;

    @Transactional(readOnly = true)
    public List<ReviewResponse> listByBook(Long userId, Long bookId) {
        bookService.findOwned(userId, bookId);

        return reviewRepository.findByBookIdOrderByCreatedAtDesc(bookId).stream()
                .map(reviewMapper::toResponse)
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

        return reviewMapper.toResponse(reviewRepository.save(review));
    }

    @Transactional
    public ReviewResponse update(Long userId, Long bookId, Long reviewId, ReviewRequest request) {
        Review review = findOwned(userId, bookId, reviewId);

        review.setRating(request.rating());
        review.setText(request.text());

        return reviewMapper.toResponse(review);
    }

    @Transactional
    public void delete(Long userId, Long bookId, Long reviewId) {
        reviewRepository.delete(findOwned(userId, bookId, reviewId));
    }

    private Review findOwned(Long userId, Long bookId, Long reviewId) {
        bookService.findOwned(userId, bookId);

        Review review = reviewRepository.findByIdAndBookId(reviewId, bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Reseña", reviewId));

        if (!review.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("La reseña pertenece a otro usuario");
        }
        return review;
    }
}
