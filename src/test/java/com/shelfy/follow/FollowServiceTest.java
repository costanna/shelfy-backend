package com.shelfy.follow;

import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.notification.NotificationService;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    private static final Long FOLLOWER_ID = 1L;
    private static final Long FOLLOWED_ID = 2L;

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Follow> followCaptor;

    private FollowService service;

    @BeforeEach
    void setUp() {
        service = new FollowService(followRepository, userRepository, notificationService);
    }

    @Test
    void follow_throwsWhenTryingToFollowYourself() {
        assertThatThrownBy(() -> service.follow(FOLLOWER_ID, FOLLOWER_ID))
                .isInstanceOf(IllegalArgumentException.class);
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_throwsWhenAlreadyFollowingThatUser() {
        when(followRepository.existsByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.follow(FOLLOWER_ID, FOLLOWED_ID))
                .isInstanceOf(DuplicateResourceException.class);
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_throwsWhenTheTargetUserDoesNotExist() {
        when(followRepository.existsByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(false);
        when(userRepository.findById(FOLLOWED_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.follow(FOLLOWER_ID, FOLLOWED_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void follow_savesTheRelationshipAndNotifiesTheFollowedUser() {
        when(followRepository.existsByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(false);
        when(userRepository.getReferenceById(FOLLOWER_ID)).thenReturn(User.builder().id(FOLLOWER_ID).build());
        when(userRepository.findById(FOLLOWED_ID))
                .thenReturn(Optional.of(User.builder().id(FOLLOWED_ID).build()));

        service.follow(FOLLOWER_ID, FOLLOWED_ID);

        verify(followRepository).save(followCaptor.capture());
        assertThat(followCaptor.getValue().getFollower().getId()).isEqualTo(FOLLOWER_ID);
        assertThat(followCaptor.getValue().getFollowed().getId()).isEqualTo(FOLLOWED_ID);
        verify(notificationService).notifyNewFollower(FOLLOWED_ID, FOLLOWER_ID);
    }

    @Test
    void unfollow_removesTheRelationshipWhenItExists() {
        Follow follow = Follow.builder().id(5L).build();
        when(followRepository.findByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID))
                .thenReturn(Optional.of(follow));

        service.unfollow(FOLLOWER_ID, FOLLOWED_ID);

        verify(followRepository).delete(follow);
    }

    @Test
    void unfollow_doesNothingWhenThereIsNoRelationshipToRemove() {
        when(followRepository.findByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID))
                .thenReturn(Optional.empty());

        service.unfollow(FOLLOWER_ID, FOLLOWED_ID);

        verify(followRepository, never()).delete(any());
    }

    @Test
    void isFollowing_delegatesToTheRepository() {
        when(followRepository.existsByFollowerIdAndFollowedId(FOLLOWER_ID, FOLLOWED_ID)).thenReturn(true);

        assertThat(service.isFollowing(FOLLOWER_ID, FOLLOWED_ID)).isTrue();
    }

    @Test
    void followersAndFollowingCounts_delegateToTheRepository() {
        when(followRepository.countByFollowedId(FOLLOWED_ID)).thenReturn(3L);
        when(followRepository.countByFollowerId(FOLLOWER_ID)).thenReturn(7L);

        assertThat(service.followersCount(FOLLOWED_ID)).isEqualTo(3L);
        assertThat(service.followingCount(FOLLOWER_ID)).isEqualTo(7L);
    }
}
