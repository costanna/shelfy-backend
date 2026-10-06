package com.shelfy.push;

import com.shelfy.push.dto.PushSubscriptionRequest;
import com.shelfy.user.User;
import com.shelfy.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;
    @Mock
    private UserRepository userRepository;

    private PushService service;

    @BeforeEach
    void setUp() {
        service = new PushService(subscriptionRepository, userRepository);
    }

    @Test
    void subscribe_savesNewSubscriptionWhenEndpointUnknown() {
        when(subscriptionRepository.findByEndpointAndOwnerId("ep", 1L)).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(1L)).thenReturn(User.builder().id(1L).build());

        service.subscribe(1L, new PushSubscriptionRequest("ep", "p256", "auth"));

        verify(subscriptionRepository).save(any(PushSubscription.class));
    }

    @Test
    void subscribe_updatesKeysWhenEndpointAlreadyKnown() {
        PushSubscription existing = PushSubscription.builder().endpoint("ep").p256dh("old").auth("old").build();
        when(subscriptionRepository.findByEndpointAndOwnerId("ep", 1L)).thenReturn(Optional.of(existing));

        service.subscribe(1L, new PushSubscriptionRequest("ep", "new-p", "new-a"));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void unsubscribe_delegatesToRepository() {
        service.unsubscribe(1L, "ep");

        verify(subscriptionRepository).deleteByEndpointAndOwnerId("ep", 1L);
    }
}
