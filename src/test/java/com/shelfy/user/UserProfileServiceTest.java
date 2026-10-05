package com.shelfy.user;

import com.shelfy.book.Book;
import com.shelfy.book.BookRepository;
import com.shelfy.book.BookStatus;
import com.shelfy.category.CategoryMapper;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowService;
import com.shelfy.review.Review;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.dto.UserProfileResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long VIEWER_ID = 1L;
    private static final Long TARGET_ID = 2L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private FollowService followService;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(userRepository, bookRepository, reviewRepository,
                new CategoryMapper(), followService);
    }

    private User target() {
        return User.builder().id(TARGET_ID).name("Autora").alias("autora99").build();
    }

    @Test
    void getProfile_throwsWhenTheTargetUserDoesNotExist() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(VIEWER_ID, TARGET_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getProfile_ofYourOwnProfileIsAlwaysVisibleWithoutCheckingFollowStatus() {
        when(userRepository.findById(VIEWER_ID)).thenReturn(Optional.of(User.builder().id(VIEWER_ID).build()));
        when(bookRepository.findByOwnerId(VIEWER_ID)).thenReturn(List.of());
        when(reviewRepository.findByUserId(VIEWER_ID)).thenReturn(List.of());

        UserProfileResponse profile = service.getProfile(VIEWER_ID, VIEWER_ID);

        assertThat(profile.own()).isTrue();
        assertThat(profile.visible()).isTrue();
        assertThat(profile.followedByMe()).isTrue();
        verify(followService, never()).isFollowing(anyLong(), anyLong());
    }

    @Test
    void getProfile_hidesTheBooksOfAStrangerYouDoNotFollow() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target()));
        when(followService.isFollowing(VIEWER_ID, TARGET_ID)).thenReturn(false);

        UserProfileResponse profile = service.getProfile(VIEWER_ID, TARGET_ID);

        assertThat(profile.visible()).isFalse();
        assertThat(profile.books()).isEmpty();
        verify(bookRepository, never()).findByOwnerId(TARGET_ID);
    }

    @Test
    void getProfile_showsTheBooksOfSomeoneYouFollow() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target()));
        when(followService.isFollowing(VIEWER_ID, TARGET_ID)).thenReturn(true);
        Book book = Book.builder().id(5L).title("Dune").status(BookStatus.READ)
                .createdAt(Instant.now()).categories(java.util.Set.of()).build();
        when(bookRepository.findByOwnerId(TARGET_ID)).thenReturn(List.of(book));
        when(reviewRepository.findByUserId(TARGET_ID)).thenReturn(List.of());

        UserProfileResponse profile = service.getProfile(VIEWER_ID, TARGET_ID);

        assertThat(profile.visible()).isTrue();
        assertThat(profile.books()).hasSize(1);
        assertThat(profile.books().get(0).title()).isEqualTo("Dune");
    }

    @Test
    void getProfile_attachesEachReviewToItsOwnBookAndSortsBooksByMostRecentFirst() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target()));
        when(followService.isFollowing(VIEWER_ID, TARGET_ID)).thenReturn(true);

        Book older = Book.builder().id(1L).title("Antiguo").status(BookStatus.READ)
                .createdAt(Instant.now().minusSeconds(100)).categories(java.util.Set.of()).build();
        Book newer = Book.builder().id(2L).title("Nuevo").status(BookStatus.READ)
                .createdAt(Instant.now()).categories(java.util.Set.of()).build();
        when(bookRepository.findByOwnerId(TARGET_ID)).thenReturn(List.of(older, newer));

        Review review = Review.builder().id(9L).book(newer).rating(new BigDecimal("4.5")).text("Bien").build();
        when(reviewRepository.findByUserId(TARGET_ID)).thenReturn(List.of(review));

        UserProfileResponse profile = service.getProfile(VIEWER_ID, TARGET_ID);

        assertThat(profile.books()).extracting("title").containsExactly("Nuevo", "Antiguo");
        assertThat(profile.books().get(0).reviews()).hasSize(1);
        assertThat(profile.books().get(1).reviews()).isEmpty();
    }

    @Test
    void getProfile_includesFollowerAndFollowingCounts() {
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target()));
        when(followService.isFollowing(VIEWER_ID, TARGET_ID)).thenReturn(false);
        when(followService.followersCount(TARGET_ID)).thenReturn(12L);
        when(followService.followingCount(TARGET_ID)).thenReturn(3L);

        UserProfileResponse profile = service.getProfile(VIEWER_ID, TARGET_ID);

        assertThat(profile.followersCount()).isEqualTo(12L);
        assertThat(profile.followingCount()).isEqualTo(3L);
    }
}
