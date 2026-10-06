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

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ReviewLikeRepository reviewLikeRepository;
    @Mock
    private ReviewCommentRepository reviewCommentRepository;
    @Mock
    private BookService bookService;
    @Mock
    private UserService userService;
    @Mock
    private com.shelfy.follow.FollowService followService;
    @Mock
    private com.shelfy.notification.NotificationService notificationService;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, reviewLikeRepository, reviewCommentRepository,
                new ReviewMapper(), bookService, userService, followService, notificationService);
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
                .isInstanceOf(ResourceNotFoundException.class);
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
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void like_createsLikeAndNotifiesTheAuthorWhenVisibleThroughFollow() {
        Review someoneElses = review(OTHER_USER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(someoneElses));
        when(followService.isFollowing(USER_ID, OTHER_USER_ID)).thenReturn(true);
        when(reviewLikeRepository.findByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(Optional.empty());
        when(userService.getEntity(USER_ID)).thenReturn(user(USER_ID, "Lectora"));
        when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);

        ReviewResponse response = service.like(USER_ID, REVIEW_ID);

        assertThat(response.likedByMe()).isTrue();
        assertThat(response.likesCount()).isEqualTo(1);
        verify(reviewLikeRepository).save(org.mockito.ArgumentMatchers.any(ReviewLike.class));
        verify(notificationService).notifyReviewLiked(eq(OTHER_USER_ID), eq(USER_ID),
                any(), any());
    }

    @Test
    void like_throwsWhenTheReviewIsNotVisible() {
        Review someoneElses = review(OTHER_USER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(someoneElses));
        when(followService.isFollowing(USER_ID, OTHER_USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.like(USER_ID, REVIEW_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unlike_removesTheLikeWhenItExists() {
        Review someoneElses = review(OTHER_USER_ID);
        ReviewLike like = ReviewLike.builder().id(7L).review(someoneElses).build();
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(someoneElses));
        when(followService.isFollowing(USER_ID, OTHER_USER_ID)).thenReturn(true);
        when(reviewLikeRepository.findByReviewIdAndUserId(REVIEW_ID, USER_ID)).thenReturn(Optional.of(like));
        when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);

        ReviewResponse response = service.unlike(USER_ID, REVIEW_ID);

        assertThat(response.likedByMe()).isFalse();
        verify(reviewLikeRepository).delete(like);
    }

    @Test
    void comment_createsCommentAndNotifiesTheAuthor() {
        Review someoneElses = review(OTHER_USER_ID);
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(someoneElses));
        when(followService.isFollowing(USER_ID, OTHER_USER_ID)).thenReturn(true);
        when(userService.getEntity(USER_ID)).thenReturn(user(USER_ID, "Lectora"));
        when(reviewCommentRepository.save(org.mockito.ArgumentMatchers.any(ReviewComment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service.comment(USER_ID, REVIEW_ID, new com.shelfy.review.dto.ReviewCommentRequest("Totalment d'acord"));

        assertThat(response.text()).isEqualTo("Totalment d'acord");
        verify(notificationService).notifyReviewCommented(eq(OTHER_USER_ID), eq(USER_ID), any(), any());
    }

    @Test
    void deleteComment_throwsWhenTheCommentBelongsToAnotherUser() {
        Review someoneElses = review(OTHER_USER_ID);
        ReviewComment comment = ReviewComment.builder().id(9L).review(someoneElses)
                .user(user(OTHER_USER_ID, "Altra")).text("Hola").build();
        when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(someoneElses));
        when(followService.isFollowing(USER_ID, OTHER_USER_ID)).thenReturn(true);
        when(reviewCommentRepository.findByIdAndReviewId(9L, REVIEW_ID)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.deleteComment(USER_ID, REVIEW_ID, 9L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
