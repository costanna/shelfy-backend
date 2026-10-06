package com.shelfy.user;

import com.shelfy.book.BookRepository;
import com.shelfy.book.ReadEventRepository;
import com.shelfy.category.CategoryRepository;
import com.shelfy.common.exception.ResourceNotFoundException;
import com.shelfy.follow.FollowRepository;
import com.shelfy.goal.ReadingGoalRepository;
import com.shelfy.note.NoteRepository;
import com.shelfy.notification.NotificationRepository;
import com.shelfy.push.PushSubscriptionRepository;
import com.shelfy.readinglog.ReadingLogRepository;
import com.shelfy.review.ReviewCommentRepository;
import com.shelfy.review.ReviewRepository;
import com.shelfy.user.dto.DeleteAccountRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ReviewCommentRepository reviewCommentRepository;
    private final ReadEventRepository readEventRepository;
    private final NotificationRepository notificationRepository;
    private final AvatarRepository avatarRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario", userId));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Contraseña incorrecta");
        }

        // Esborrats massius: sense carregar col·leccions senceres en memòria.
        readingLogRepository.deleteByOwnerId(userId);
        reviewCommentRepository.deleteByUserId(userId);
        reviewRepository.deleteByUserId(userId);
        noteRepository.deleteByUserId(userId);
        readingGoalRepository.deleteByOwnerId(userId);
        readEventRepository.deleteByOwnerId(userId);

        bookRepository.deleteJoinRowsByOwnerId(userId);
        bookRepository.deleteByOwnerId(userId);

        categoryRepository.deleteByOwnerId(userId);

        followRepository.deleteByFollowerIdOrFollowedId(userId, userId);

        notificationRepository.deleteByRecipientIdOrActorId(userId);

        pushSubscriptionRepository.deleteByOwnerId(userId);

        avatarRepository.findById(userId).ifPresent(avatarRepository::delete);

        userRepository.delete(user);
    }
}
