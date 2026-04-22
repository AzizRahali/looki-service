package com.loomea.subscription.repository;

import com.loomea.subscription.entity.Subscription;
import com.loomea.subscription.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    /** Find the most recent subscription for a user regardless of status. */
    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Find currently active subscription for a user. */
    Optional<Subscription> findByUserIdAndStatus(UUID userId, SubscriptionStatus status);

    /** Find all ACTIVE subscriptions whose endDate has passed (for expiration job). */
    @Query("SELECT s FROM Subscription s WHERE s.status = 'ACTIVE' AND s.endDate < :now")
    List<Subscription> findExpiredActive(@Param("now") Instant now);
}
