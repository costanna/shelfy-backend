package com.shelfy.review;

import com.shelfy.book.Book;
import com.shelfy.book.BookService;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.review.dto.ReviewRequest;
import com.shelfy.review.dto.ReviewResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long BOOK_ID = 10L;
    private static final Long REVIEW_ID = 100L;

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookService bookService;
    @Mock
    private UserService userService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, new ReviewMapper(), bookService, userService);
    }

    private Book book() {
        return Book.builder().id(BOOK_ID).build();
    }

    private User user(Long id, String name) {
        return User.builder().id(id).name(name).build();
    }

    private Review review(Long reviewerId) {
        return Review.builder()
                .id(REVIEW_ID)
                .book(book())
                .user(user(reviewerId, "Lectora"))
                .rating(new BigDecimal("4.0"))
                .text("Me gustó")
                .build();
    }

    @Test
    void create_savesAReviewOwnedByTheCaller() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(userService.getEntity(USER_ID)).thenReturn(user(USER_ID, "Lectora"));
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.create(USER_ID, BOOK_ID,
                new ReviewRequest(new BigDecimal("4.5"), "Genial"));

        assertThat(response.rating()).isEqualByComparingTo("4.5");
        assertThat(response.text()).isEqualTo("Genial");
    }

    @Test
    void update_changesRatingAndTextWhenTheReviewBelongsToTheCaller() {
        Review existing = review(USER_ID);
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(reviewRepository.findByIdAndBookId(REVIEW_ID, BOOK_ID)).thenReturn(Optional.of(existing));

        ReviewResponse response = service.update(USER_ID, BOOK_ID, REVIEW_ID,
                new ReviewRequest(new BigDecimal("2.0"), "Cambié de opinión"));

        assertThat(response.rating()).isEqualByComparingTo("2.0");
        assertThat(response.text()).isEqualTo("Cambié de opinión");
    }

    @Test
    void update_throwsWhenTheReviewBelongsToAnotherUser() {
        Review someoneElses = review(OTHER_USER_ID);
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(reviewRepository.findByIdAndBookId(REVIEW_ID, BOOK_ID)).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.update(USER_ID, BOOK_ID, REVIEW_ID,
                new ReviewRequest(new BigDecimal("1.0"), null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_throwsWhenTheReviewDoesNotExist() {
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(reviewRepository.findByIdAndBookId(REVIEW_ID, BOOK_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(USER_ID, BOOK_ID, REVIEW_ID,
                new ReviewRequest(new BigDecimal("1.0"), null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesTheReviewWhenItBelongsToTheCaller() {
        Review existing = review(USER_ID);
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(reviewRepository.findByIdAndBookId(REVIEW_ID, BOOK_ID)).thenReturn(Optional.of(existing));

        service.delete(USER_ID, BOOK_ID, REVIEW_ID);

        verify(reviewRepository).delete(existing);
    }

    @Test
    void delete_throwsWhenTheReviewBelongsToAnotherUser() {
        Review someoneElses = review(OTHER_USER_ID);
        when(bookService.findOwned(USER_ID, BOOK_ID)).thenReturn(book());
        when(reviewRepository.findByIdAndBookId(REVIEW_ID, BOOK_ID)).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.delete(USER_ID, BOOK_ID, REVIEW_ID))
                .isInstanceOf(AccessDeniedException.class);
    }
}
