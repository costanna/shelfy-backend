package com.shelfy.notification;

import com.shelfy.common.dto.PageResponse;
import com.shelfy.notification.dto.NotificationResponse;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long RECIPIENT_ID = 1L;
    private static final Long ACTOR_ID = 2L;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository);
    }

    private User user(Long id, String name, String alias) {
        return User.builder().id(id).name(name).alias(alias).build();
    }

    @Test
    void notifyNewFollower_savesANotificationLinkingRecipientAndActor() {
        when(userRepository.getReferenceById(RECIPIENT_ID)).thenReturn(user(RECIPIENT_ID, "Destinataria", null));
        when(userRepository.getReferenceById(ACTOR_ID)).thenReturn(user(ACTOR_ID, "Seguidor", null));

        service.notifyNewFollower(RECIPIENT_ID, ACTOR_ID);

        verify(notificationRepository).save(notificationCaptor.capture());
        Notification saved = notificationCaptor.getValue();
        assertThat(saved.getRecipient().getId()).isEqualTo(RECIPIENT_ID);
        assertThat(saved.getActor().getId()).isEqualTo(ACTOR_ID);
        assertThat(saved.getType()).isEqualTo(NotificationType.NEW_FOLLOWER);
    }

    @Test
    void list_mapsEachNotificationToAResponseWithTheActorsData() {
        Notification notification = Notification.builder()
                .id(5L)
                .recipient(user(RECIPIENT_ID, "Destinataria", null))
                .actor(user(ACTOR_ID, "Seguidor", "seguidor99"))
                .type(NotificationType.NEW_FOLLOWER)
                .readAt(null)
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(RECIPIENT_ID, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(notification), pageable, 1));

        PageResponse<NotificationResponse> response = service.list(RECIPIENT_ID, pageable);

        assertThat(response.content()).hasSize(1);
        NotificationResponse item = response.content().get(0);
        assertThat(item.actorId()).isEqualTo(ACTOR_ID);
        assertThat(item.actorAlias()).isEqualTo("seguidor99");
        assertThat(item.actorName()).isEqualTo("Seguidor");
        assertThat(item.read()).isFalse();
    }

    @Test
    void list_marksAnAlreadyReadNotificationAsRead() {
        Notification notification = Notification.builder()
                .id(5L)
                .recipient(user(RECIPIENT_ID, "Destinataria", null))
                .actor(user(ACTOR_ID, "Seguidor", null))
                .type(NotificationType.NEW_FOLLOWER)
                .readAt(Instant.now())
                .build();
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(RECIPIENT_ID, pageable))
                .thenReturn(new PageImpl<>(java.util.List.of(notification), pageable, 1));

        PageResponse<NotificationResponse> response = service.list(RECIPIENT_ID, pageable);

        assertThat(response.content().get(0).read()).isTrue();
    }

    @Test
    void unreadCount_delegatesToTheRepository() {
        when(notificationRepository.countByRecipientIdAndReadAtIsNull(RECIPIENT_ID)).thenReturn(4L);

        assertThat(service.unreadCount(RECIPIENT_ID)).isEqualTo(4L);
    }

    @Test
    void markAllRead_delegatesToTheRepositoryWithTheCurrentTime() {
        service.markAllRead(RECIPIENT_ID);

        verify(notificationRepository).markAllRead(org.mockito.ArgumentMatchers.eq(RECIPIENT_ID), any(Instant.class));
    }
}
