package com.loomea.auth.service;

import com.loomea.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Nightly job that permanently deletes accounts whose 30-day grace period has expired.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final AuthService authService;

    @Scheduled(cron = "0 30 2 * * *") // 02:30 every night
    @Transactional
    public void purgeExpiredDeletions() {
        List<UUID> due = userRepository.findUsersDueForDeletion(Instant.now());
        if (due.isEmpty()) return;
        for (UUID userId : due) {
            try {
                authService.hardDeleteAccount(userId);
                log.info("[AccountDeletion] Hard-deleted user {}", userId);
            } catch (Exception ex) {
                log.error("[AccountDeletion] Failed to delete user {}: {}", userId, ex.getMessage());
            }
        }
    }
}
