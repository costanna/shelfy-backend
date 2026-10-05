package com.shelfy.feed;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.feed.dto.FeedItemResponse;
import com.shelfy.feed.dto.FeedItemType;
import com.shelfy.follow.Follow;
import com.shelfy.follow.FollowRepository;
import com.shelfy.review.Review;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final Long VIEWER_ID = 1L;
    private static final Long FOLLOWED_ID = 2L;

    @Mock
    private FollowRepository followRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReviewRepository reviewRepository;

    private FeedService service;

    @BeforeEach
    void setUp() {
        service = new FeedService(followRepository, bookRepository, reviewRepository);
    }

    private User user(Long id, String alias) {
        return User.builder().id(id).name("Autora").alias(alias).build();
    }

    private Follow follow(Long followedId) {
        return Follow.builder().follower(user(VIEWER_ID, "yo")).followed(user(followedId, "otra")).build();
    }

    private Book book(Long id, String title, Instant updatedAt) {
        return Book.builder().id(id).title(title).owner(user(FOLLOWED_ID, "otra")).updatedAt(updatedAt).build();
    }

    @Test
    void getFeed_returnsEmptyWithoutQueryingAnythingWhenTheCallerFollowsNobody() {
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(VIEWER_ID)).thenReturn(List.of());

        List<FeedItemResponse> feed = service.getFeed(VIEWER_ID, 20);

        assertThat(feed).isEmpty();
        verify(bookRepository, never()).findByOwnerIdInAndStatusOrderByUpdatedAtDesc(any(), any(), any());
        verify(reviewRepository, never()).findByUserIdInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void getFeed_combinesStartedFinishedAndReviewedItemsSortedByMostRecentFirst() {
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(VIEWER_ID)).thenReturn(List.of(follow(FOLLOWED_ID)));

        Instant now = Instant.now();
        Book startedBook = book(10L, "Empezado", now.minusSeconds(60));
        Book finishedBook = book(11L, "Terminado", now);
        Pageable pageable = PageRequest.of(0, 20);

        when(bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(
                eq(List.of(FOLLOWED_ID)), eq(BookStatus.READING), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(startedBook)));
        when(bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(
                eq(List.of(FOLLOWED_ID)), eq(BookStatus.READ), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(finishedBook)));

        Review review = Review.builder().id(5L).user(user(FOLLOWED_ID, "otra"))
                .book(finishedBook).rating(new BigDecimal("4.0"))
                .createdAt(now.minusSeconds(30)).build();
        when(reviewRepository.findByUserIdInOrderByCreatedAtDesc(eq(List.of(FOLLOWED_ID)), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(review)));

        List<FeedItemResponse> feed = service.getFeed(VIEWER_ID, 20);

        assertThat(feed).hasSize(3);
        assertThat(feed).extracting(FeedItemResponse::type).containsExactly(
                FeedItemType.FINISHED_READING, FeedItemType.REVIEWED, FeedItemType.STARTED_READING);
        assertThat(feed.get(1).rating()).isEqualByComparingTo("4.0");
    }

    @Test
    void getFeed_truncatesToTheRequestedLimitAfterMerging() {
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(VIEWER_ID)).thenReturn(List.of(follow(FOLLOWED_ID)));

        Instant now = Instant.now();
        Page<Book> threeBooks = new PageImpl<>(List.of(
                book(1L, "A", now), book(2L, "B", now.minusSeconds(1)), book(3L, "C", now.minusSeconds(2))));
        when(bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(any(), eq(BookStatus.READING), any()))
                .thenReturn(threeBooks);
        when(bookRepository.findByOwnerIdInAndStatusOrderByUpdatedAtDesc(any(), eq(BookStatus.READ), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(reviewRepository.findByUserIdInOrderByCreatedAtDesc(any(), any())).thenReturn(new PageImpl<>(List.of()));

        List<FeedItemResponse> feed = service.getFeed(VIEWER_ID, 2);

        assertThat(feed).hasSize(2);
        assertThat(feed).extracting(FeedItemResponse::bookTitle).containsExactly("A", "B");
    }
}
