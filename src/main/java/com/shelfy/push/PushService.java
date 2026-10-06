package com.shelfy.push;

import com.shelfy.push.dto.PushSubscriptionRequest;
import com.shelfy.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;

/**
 * Subscripcions Web Push + enviament via VAPID
 * (llibreria {@code nl.martijndorsman:web-push}).
 *
 * Sense claus VAPID configurades ({@code VAPID_PUBLIC_KEY}/{@code VAPID_PRIVATE_KEY}),
 * les subscripcions es guarden però no s'envia res (mode registre amb log).
 * Genera claus amb: {@code npx web-push generate-vapid-keys}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {

    private final PushSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @Value("${shelfy.push.vapid-public-key:}")
    private String vapidPublicKey;

    @Value("${shelfy.push.vapid-private-key:}")
    private String vapidPrivateKey;

    @Value("${shelfy.push.vapid-subject:mailto:shelfy@example.com}")
    private String vapidSubject;

    private volatile nl.martijndwars.webpush.PushService sender;

    @Transactional
    public void subscribe(Long ownerId, PushSubscriptionRequest request) {
        subscriptionRepository.findByEndpointAndOwnerId(request.endpoint(), ownerId)
                .ifPresentOrElse(
                        existing -> {
                            existing.setP256dh(request.p256dh());
                            existing.setAuth(request.auth());
                        },
                        () -> subscriptionRepository.save(PushSubscription.builder()
                                .owner(userRepository.getReferenceById(ownerId))
                                .endpoint(request.endpoint())
                                .p256dh(request.p256dh())
                                .auth(request.auth())
                                .build()));
    }

    @Transactional
    public void unsubscribe(Long ownerId, String endpoint) {
        subscriptionRepository.deleteByEndpointAndOwnerId(endpoint, ownerId);
    }

    /**
     * Envia un push a totes les subscripcions del destinatari. Les
     * subscripcions caducades (404/410 del push service) s'eliminen soles.
     */
    @Transactional
    public void notifyNewFollower(Long recipientId, Long actorId, String actorName) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByOwnerId(recipientId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String body = actorName != null ? actorName + " ha començat a seguir-te" : "Algú ha començat a seguir-te";
        String payload = "{\"title\":" + json("Tens un nou seguidor")
                + ",\"body\":" + json(body)
                + ",\"url\":" + json("/people/" + actorId) + "}";
        sendToAll(subscriptions, payload.getBytes(StandardCharsets.UTF_8));
    }

    @Transactional
    public void sendToUser(Long recipientId, String title, String body, String url) {
        List<PushSubscription> subscriptions = subscriptionRepository.findByOwnerId(recipientId);
        if (subscriptions.isEmpty()) {
            return;
        }
        String payload = "{\"title\":" + json(title)
                + ",\"body\":" + json(body)
                + (url != null ? ",\"url\":" + json(url) : "") + "}";
        sendToAll(subscriptions, payload.getBytes(StandardCharsets.UTF_8));
    }

    private void sendToAll(List<PushSubscription> subscriptions, byte[] payload) {
        nl.martijndwars.webpush.PushService sender = sender();
        if (sender == null) {
            log.info("[push pendent, sense claus VAPID] subscripcions={}", subscriptions.size());
            return;
        }
        for (PushSubscription subscription : subscriptions) {
            try {
                nl.martijndwars.webpush.Notification notification =
                        new nl.martijndwars.webpush.Notification(
                                subscription.getEndpoint(),
                                subscription.getP256dh(),
                                subscription.getAuth(),
                                payload);
                org.apache.http.HttpResponse response = sender.send(notification);
                int status = response.getStatusLine().getStatusCode();
                if (status == 404 || status == 410) {
                    subscriptionRepository.delete(subscription);
                    log.info("[push] subscripció caducada eliminada: {}", subscription.getId());
                } else if (status < 200 || status >= 300) {
                    log.warn("[push] enviament fallit amb estat {}", status);
                }
            } catch (Exception ex) {
                log.warn("[push] no s'ha pogut enviar a {}: {}", subscription.getId(), ex.getMessage());
            }
        }
    }

    private nl.martijndwars.webpush.PushService sender() {
        if (vapidPublicKey == null || vapidPublicKey.isBlank()
                || vapidPrivateKey == null || vapidPrivateKey.isBlank()) {
            return null;
        }
        nl.martijndwars.webpush.PushService cached = sender;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (sender != null) {
                return sender;
            }
            try {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
                nl.martijndwars.webpush.PushService created =
                        new nl.martijndwars.webpush.PushService(vapidPublicKey, vapidPrivateKey);
                created.setSubject(vapidSubject);
                sender = created;
                return created;
            } catch (Exception ex) {
                log.warn("[push] claus VAPID no vàlides: {}", ex.getMessage());
                return null;
            }
        }
    }

    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
