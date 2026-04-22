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
            throw new BadRequestException( "Subscription plan is required");
        }
        validateCardDetails(req);

        // Cancel any existing active subscription before creating a new one
        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(existing);
                });

        SubscriptionPlan plan = req.getPlan();
        String last4 = extractLast4(req.getCardNumber());
        String payRef = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(plan.getDurationDays(), ChronoUnit.DAYS))
                .paymentReference(payRef)
                .cardLast4(last4)
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

    private void validateCardDetails(SubscriptionRequest req) {
        if (req.getCardNumber() == null || req.getCardNumber().replaceAll("\\s", "").length() < 12) {
            throw new BadRequestException( "Invalid card number");
        }
        if (req.getCardExpiry() == null || !req.getCardExpiry().matches("\\d{2}/\\d{2}")) {
            throw new BadRequestException( "Invalid card expiry (MM/YY)");
        }
        if (req.getCardCvv() == null || req.getCardCvv().length() < 3) {
            throw new BadRequestException( "Invalid CVV");
        }
    }

    private String extractLast4(String cardNumber) {
        if (cardNumber == null) return "0000";
        String digits = cardNumber.replaceAll("\\D", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : "0000";
    }

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
                .paymentReference(sub.getPaymentReference())
                .cardLast4(sub.getCardLast4())
                .build();
    }
}
