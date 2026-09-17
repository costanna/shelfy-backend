package com.shelfy.notification;

import com.shelfy.common.dto.PageResponse;
import com.shelfy.notification.dto.NotificationResponse;
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

    @Transactional
    public void notifyNewFollower(Long recipientId, Long actorId) {
        User recipient = userRepository.getReferenceById(recipientId);
        User actor = userRepository.getReferenceById(actorId);

        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.NEW_FOLLOWER)
                .build());
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
