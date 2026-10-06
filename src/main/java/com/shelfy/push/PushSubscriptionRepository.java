package com.shelfy.push;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointAndOwnerId(String endpoint, Long ownerId);

    List<PushSubscription> findByOwnerId(Long ownerId);

    void deleteByOwnerId(Long ownerId);

    void deleteByEndpointAndOwnerId(String endpoint, Long ownerId);
}
