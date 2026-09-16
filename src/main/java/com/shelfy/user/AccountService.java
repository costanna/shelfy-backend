package com.shelfy.user;

import com.shelfy.book.BookRepository;
import com.shelfy.category.CategoryRepository;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowRepository;
import com.shelfy.goal.ReadingGoalRepository;
import com.shelfy.note.NoteRepository;
import com.shelfy.readinglog.ReadingLogRepository;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.dto.DeleteAccountRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final ReviewRepository reviewRepository;
    private final NoteRepository noteRepository;
    private final ReadingLogRepository readingLogRepository;
    private final ReadingGoalRepository readingGoalRepository;
    private final FollowRepository followRepository;
    private final AvatarRepository avatarRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Contraseña incorrecta");
        }

        readingLogRepository.deleteAll(readingLogRepository.findByOwnerId(userId));
        reviewRepository.deleteAll(reviewRepository.findByUserId(userId));
        noteRepository.deleteAll(noteRepository.findByUserId(userId));
        readingGoalRepository.deleteAll(readingGoalRepository.findByOwnerId(userId));

        bookRepository.deleteAll(bookRepository.findByOwnerId(userId));

        categoryRepository.deleteAll(categoryRepository.findByOwnerIdOrderByNameAsc(userId));

        var followingAndFollowers = new LinkedHashSet<>(followRepository.findByFollowerIdOrderByCreatedAtDesc(userId));
        followingAndFollowers.addAll(followRepository.findByFollowedIdOrderByCreatedAtDesc(userId));
        followRepository.deleteAll(followingAndFollowers);

        avatarRepository.findById(userId).ifPresent(avatarRepository::delete);

        userRepository.delete(user);
    }
}
