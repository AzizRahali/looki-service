package com.looki.booking.service;

import com.looki.auth.entity.User;
import com.looki.auth.repository.UserRepository;
import com.looki.common.reliability.ReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Runs once per day (06:00) and awards +1 reliability point to every user who:
 * <ul>
 *   <li>Has not had a negative action in the past 7 days</li>
 *   <li>Has not already received passive recovery in the past 7 days</li>
 *   <li>Has a score below the maximum (120)</li>
 * </ul>
 *
 * This provides a natural "forgiveness" path for users who simply stop behaving badly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PassiveRecoveryService {

    private final UserRepository userRepository;
    private final ReliabilityService reliabilityService;

    /** Every day at 06:00 server time. */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void runDailyRecovery() {
        List<User> candidates = userRepository.findUsersWithScoreBelowMax(
                ReliabilityService.MAX_SCORE);

        int recovered = 0;
        for (User user : candidates) {
            if (reliabilityService.applyPassiveRecovery(user)) {
                recovered++;
            }
        }

        if (recovered > 0) {
            log.info("Passive recovery: awarded +1 point to {} user(s)", recovered);
        }
    }
}
