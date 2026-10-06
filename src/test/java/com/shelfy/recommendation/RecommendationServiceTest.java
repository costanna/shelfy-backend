package com.shelfy.recommendation;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.follow.Follow;
import com.shelfy.follow.FollowRepository;
import com.shelfy.recommendation.dto.RecommendationResponse;
import com.shelfy.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Long OWNER_ID = 1L;

    @Mock
    private BookRepository bookRepository;
    @Mock
    private FollowRepository followRepository;

    private RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(bookRepository, followRepository);
    }

    private Book book(Long id, Long ownerId, String title, String author, BookStatus status) {
        return Book.builder().id(id).title(title).author(author).status(status)
                .owner(User.builder().id(ownerId).build()).build();
    }

    private Follow follow(Long followedId) {
        return Follow.builder()
                .follower(User.builder().id(OWNER_ID).build())
                .followed(User.builder().id(followedId).alias("lector" + followedId).build())
                .build();
    }

    @Test
    void returnsEmptyWhenNobodyIsFollowed() {
        when(bookRepository.findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                List.of(OWNER_ID), BookStatus.READ)).thenReturn(List.of(
                        book(1L, OWNER_ID, "Dune", "Frank Herbert", BookStatus.READ)));
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of());
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(OWNER_ID)).thenReturn(List.of());

        RecommendationResponse response = service.getRecommendations(OWNER_ID);

        assertThat(response.basedOnAuthor()).isEqualTo("Frank Herbert");
        assertThat(response.items()).isEmpty();
    }

    @Test
    void suggestsFollowedReadsNotOwnedRankedByReaderCount() {
        Book ownDune = book(1L, OWNER_ID, "Dune", "Frank Herbert", BookStatus.READ);
        when(bookRepository.findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                List.of(OWNER_ID), BookStatus.READ)).thenReturn(List.of(ownDune));
        when(bookRepository.findByOwnerIdAndDeletedAtIsNull(OWNER_ID)).thenReturn(List.of(ownDune));
        when(followRepository.findByFollowerIdOrderByCreatedAtDesc(OWNER_ID))
                .thenReturn(List.of(follow(2L), follow(3L)));
        when(bookRepository.findByOwnerIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                eq(List.of(2L, 3L)), eq(BookStatus.READ))).thenReturn(List.of(
                        book(10L, 2L, "Dune", "Frank Herbert", BookStatus.READ),
                        book(11L, 2L, "Neuromant", "William Gibson", BookStatus.READ),
                        book(12L, 3L, "Neuromant", "William Gibson", BookStatus.READ),
                        book(13L, 3L, "Snow Crash", "Neal Stephenson", BookStatus.READ)));

        RecommendationResponse response = service.getRecommendations(OWNER_ID);

        assertThat(response.items()).extracting(item -> item.title())
                .containsExactly("Neuromant", "Snow Crash");
        assertThat(response.items().get(0).readerCount()).isEqualTo(2);
    }
}
