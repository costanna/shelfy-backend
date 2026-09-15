package com.shelfy.follow;

import com.shelfy.common.exception.DuplicateResourceException;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Transactional
    public void follow(Long followerId, Long followedId) {
        if (followerId.equals(followedId)) {
            throw new IllegalArgumentException("No puedes seguirte a ti mismo");
        }
        if (followRepository.existsByFollowerIdAndFollowedId(followerId, followedId)) {
            throw new DuplicateResourceException("Ya sigues a este usuario");
        }

        User follower = userRepository.getReferenceById(followerId);
        User followed = userRepository.findById(followedId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", followedId));

        followRepository.save(Follow.builder()
                .follower(follower)
                .followed(followed)
                .build());
    }

    @Transactional
    public void unfollow(Long followerId, Long followedId) {
        followRepository.findByFollowerIdAndFollowedId(followerId, followedId)
                .ifPresent(followRepository::delete);
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followedId) {
        return followRepository.existsByFollowerIdAndFollowedId(followerId, followedId);
    }

    @Transactional(readOnly = true)
    public long followersCount(Long userId) {
        return followRepository.countByFollowedId(userId);
    }

    @Transactional(readOnly = true)
    public long followingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}
