package com.loomea.subscription.service;

import com.loomea.common.exception.BadRequestException;
import com.loomea.subscription.dto.SubscriptionDto;
import com.loomea.subscription.dto.SubscriptionRequest;
import com.loomea.subscription.entity.Subscription;
import com.loomea.subscription.entity.SubscriptionPlan;
import com.loomea.subscription.entity.SubscriptionStatus;
import com.loomea.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create (or renew) a subscription for a salon owner.
     * Any existing ACTIVE subscription is cancelled first so there is always at
     * most one active entry per owner.
     */
    @Transactional
    public SubscriptionDto subscribe(UUID userId, SubscriptionRequest req) {
        if (req.getPlan() == null) {
            throw new BadRequestException("Subscription plan is required");
        }

        // Cancel any existing active subscription before creating a new one
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(existing);
                });

        SubscriptionPlan plan = req.getPlan();

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(plan.getDurationDays(), ChronoUnit.DAYS))
                .build();

        return toDto(subscriptionRepository.save(sub));
    }

    /**
     * Returns the most recent subscription for a user, or empty if none exists.
     */
    @Transactional(readOnly = true)
    public Optional<SubscriptionDto> getMySubscription(UUID userId) {
        return subscriptionRepository
                .findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toDto);
    }

    /**
     * Returns true if the user has an active (non-expired, non-cancelled) subscription.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveSubscription(UUID userId) {
        return subscriptionRepository
                .findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .isPresent();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    public SubscriptionDto toDto(Subscription sub) {
        return SubscriptionDto.builder()
                .id(sub.getId().toString())
                .plan(sub.getPlan().name())
                .planLabel(sub.getPlan().getLabel())
                .priceLabel(sub.getPlan().getPriceLabel())
                .price(sub.getPlan().getPrice())
                .status(sub.getStatus().name())
                .startDate(sub.getStartDate().toString())
                .endDate(sub.getEndDate().toString())
                .build();
    }
}
