package com.shelfy.notification;

import com.shelfy.common.dto.PageResponse;
import com.shelfy.notification.dto.NotificationResponse;
import com.shelfy.push.PushService;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushService pushService;

    @Transactional
    public void notifyNewFollower(Long recipientId, Long actorId) {
        User recipient = userRepository.getReferenceById(recipientId);
        User actor = userRepository.getReferenceById(actorId);

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.NEW_FOLLOWER)
                .build());

        pushService.notifyNewFollower(recipientId, actorId, actor.getName());
    }

    @Transactional
    public void notifyReviewLiked(Long recipientId, Long actorId, String actorName, String bookTitle) {
        User recipient = userRepository.getReferenceById(recipientId);
        User actor = userRepository.getReferenceById(actorId);

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.NEW_REVIEW_LIKE)
                .build());

        String title = bookTitle != null
                ? "A " + actorName + " li agrada la teva ressenya de «" + bookTitle + "»"
                : "A " + actorName + " li agrada la teva ressenya";
        pushService.sendToUser(recipientId, "Nova m'agrada", title, null);
    }

    @Transactional
    public void notifyReviewCommented(Long recipientId, Long actorId, String actorName, String bookTitle) {
        User recipient = userRepository.getReferenceById(recipientId);
        User actor = userRepository.getReferenceById(actorId);

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.NEW_REVIEW_COMMENT)
                .build());

        String title = bookTitle != null
                ? actorName + " ha comentat la teva ressenya de «" + bookTitle + "»"
                : actorName + " ha comentat la teva ressenya";
        pushService.sendToUser(recipientId, "Nou comentari", title, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(Long userId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    private NotificationResponse toResponse(Notification notification) {
        User actor = notification.getActor();
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                actor.getId(),
                actor.getAlias(),
                actor.getName(),
                notification.getReadAt() != null,
                notification.getCreatedAt()
        );
    }
}
