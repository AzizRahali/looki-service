package com.looki.subscription.service;

import com.looki.subscription.entity.Subscription;
import com.looki.subscription.entity.SubscriptionStatus;
import com.looki.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Daily job that marks ACTIVE subscriptions as EXPIRED once their endDate passes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionExpirationService {

    private final SubscriptionRepository subscriptionRepository;

    @Scheduled(cron = "0 0 2 * * *") // every day at 02:00
    @Transactional
    public void expireSubscriptions() {
        List<Subscription> expired = subscriptionRepository.findExpiredActive(Instant.now());
        if (expired.isEmpty()) return;

        for (Subscription sub : expired) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
        }

        log.info("[SubscriptionExpiration] Expired {} subscription(s)", expired.size());
    }
}
