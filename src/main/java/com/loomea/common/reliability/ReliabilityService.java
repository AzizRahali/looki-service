package com.loomea.common.reliability;

import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Core reliability-score engine.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Apply score mutations for completions, no-shows, and late cancellations</li>
 *   <li>Passive daily recovery (+1 per 7-day clean window)</li>
 *   <li>Evaluate restrictions (block, deposit, max bookings, confirmation)</li>
 * </ul>
 *
 * <p>This class is intentionally decoupled from booking logic — it receives a
 * {@link User}, mutates the score, persists, and returns. Callers decide when
 * to invoke it.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReliabilityService {

    // ── Score bounds ───────────────────────────────────────────────────────────
    public static final int MAX_SCORE = 120;
    public static final int MIN_SCORE = 0;

    // ── Score deltas ───────────────────────────────────────────────────────────
    private static final int COMPLETION_REWARD       = 2;
    private static final int COMPLETION_STREAK_BONUS = 5;
    private static final int COMPLETION_STREAK_AT    = 3;   // every N completions
    private static final int NO_SHOW_PENALTY         = 20;  // subtracted
    private static final int LATE_CANCEL_PENALTY     = 10;  // subtracted
    private static final int PASSIVE_RECOVERY_DELTA  = 1;

    // ── Restriction thresholds ─────────────────────────────────────────────────
    /** Hours before appointment start; cancellations inside this window are "late". */
    public static final int  LATE_CANCEL_HOURS           = 12;
    /** Score below which attendance confirmation is required. */
    public static final int  REQUIRE_CONFIRMATION_BELOW  = 50;
    /** Score below which active-booking count is capped at {@code MAX_ACTIVE_RISKY}. */
    public static final int  LIMIT_ACTIVE_BOOKINGS_BELOW = 30;
    /** Score below which a deposit must be paid before booking. */
    public static final int  REQUIRE_DEPOSIT_BELOW       = 20;
    /** Score below which bookings are temporarily blocked. */
    public static final int  BLOCK_BOOKING_BELOW         = 10;

    public static final int  MAX_ACTIVE_RISKY       = 2;
    public static final int  MAX_ACTIVE_RESTRICTED  = 1;
    public static final int  BLOCK_DURATION_DAYS    = 7;
    public static final int  PASSIVE_RECOVERY_DAYS  = 7;

    private final UserRepository userRepository;

    // ── Tier evaluation ────────────────────────────────────────────────────────

    public ReliabilityTier getTier(User user) {
        return ReliabilityTier.fromScore(user.getReliabilityScore());
    }

    // ── Score mutations ────────────────────────────────────────────────────────

    /**
     * Award +2 for a completed appointment.
     * Tracks a consecutive-completion streak; awards a +5 bonus every 3 in a row.
     */
    public void applyCompletion(User user) {
        int streak = user.getConsecutiveCompletions() + 1;
        user.setConsecutiveCompletions(streak);

        int delta = COMPLETION_REWARD;
        if (streak % COMPLETION_STREAK_AT == 0) {
            delta += COMPLETION_STREAK_BONUS;
            log.info("User {} hit streak of {} — bonus +{}", user.getId(), streak, COMPLETION_STREAK_BONUS);
        }

        int before = user.getReliabilityScore();
        adjustScore(user, delta);
        log.info("User {} completion: score {} → {}", user.getId(), before, user.getReliabilityScore());
        userRepository.save(user);
    }

    /**
     * Penalise -20 for a no-show. Resets the streak and may trigger a booking block.
     */
    public void applyNoShow(User user) {
        user.setConsecutiveCompletions(0);
        user.setLastNegativeActionAt(Instant.now());

        int before = user.getReliabilityScore();
        adjustScore(user, -NO_SHOW_PENALTY);
        log.warn("User {} no-show: score {} → {}", user.getId(), before, user.getReliabilityScore());
        userRepository.save(user);
    }

    /**
     * Penalise -10 for a late cancellation (< {@value LATE_CANCEL_HOURS} hours before start).
     * Resets the streak and may trigger a booking block.
     */
    public void applyLateCancellation(User user) {
        user.setConsecutiveCompletions(0);
        user.setLastNegativeActionAt(Instant.now());

        int before = user.getReliabilityScore();
        adjustScore(user, -LATE_CANCEL_PENALTY);
        log.warn("User {} late cancel: score {} → {}", user.getId(), before, user.getReliabilityScore());
        userRepository.save(user);
    }

    /**
     * Passive recovery: +1 point if the user has been clean for ≥ 7 days and hasn't
     * already been recovered in this 7-day window.
     *
     * @return {@code true} if recovery was applied
     */
    public boolean applyPassiveRecovery(User user) {
        if (user.getReliabilityScore() >= MAX_SCORE) return false;

        Instant window = Instant.now().minus(PASSIVE_RECOVERY_DAYS, ChronoUnit.DAYS);

        // Any negative action in the window blocks recovery
        if (user.getLastNegativeActionAt() != null
                && user.getLastNegativeActionAt().isAfter(window)) {
            return false;
        }

        // Only one passive recovery per 7-day window
        if (user.getLastPassiveRecoveryAt() != null
                && user.getLastPassiveRecoveryAt().isAfter(window)) {
            return false;
        }

        adjustScore(user, PASSIVE_RECOVERY_DELTA);
        user.setLastPassiveRecoveryAt(Instant.now());
        userRepository.save(user);
        return true;
    }

    // ── Restriction queries ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the user's booking-block period is still active.
     * Clears an expired block automatically.
     */
    public boolean isBlocked(User user) {
        if (user.getBookingBlockedUntil() == null) return false;
        if (Instant.now().isBefore(user.getBookingBlockedUntil())) return true;
        // Block expired — clear it
        user.setBookingBlockedUntil(null);
        userRepository.save(user);
        return false;
    }

    /**
     * Returns a human-readable reason the user cannot book, or {@code null} if they can.
     */
    public String getBlockReason(User user) {
        if (isBlocked(user)) {
            String until = user.getBookingBlockedUntil().toString().substring(0, 10);
            return "Your booking access is temporarily suspended due to repeated no-shows or "
                    + "late cancellations. Access resumes on " + until + ".";
        }
        if (requiresDeposit(user)) {
            return "Your reliability score is too low. A deposit is required before booking. "
                    + "Please contact support or complete existing appointments to recover your score.";
        }
        return null;
    }

    /**
     * Maximum simultaneous active (PENDING + CONFIRMED) bookings for this user.
     * Returns {@link Integer#MAX_VALUE} for unrestricted users.
     */
    public int getMaxActiveBookings(User user) {
        int score = user.getReliabilityScore();
        if (score < LIMIT_ACTIVE_BOOKINGS_BELOW) return MAX_ACTIVE_RESTRICTED;
        if (score < REQUIRE_CONFIRMATION_BELOW)  return MAX_ACTIVE_RISKY;
        return Integer.MAX_VALUE;
    }

    /** Whether this user must confirm attendance before their appointment. */
    public boolean requiresConfirmation(User user) {
        return user.getReliabilityScore() < REQUIRE_CONFIRMATION_BELOW;
    }

    /** Whether a deposit must be collected before confirming this user's booking. */
    public boolean requiresDeposit(User user) {
        return user.getReliabilityScore() < REQUIRE_DEPOSIT_BELOW;
    }

    // ── Internal helpers ───────────────────────────────────────────────────────

    /**
     * Clamps the score to [MIN_SCORE, MAX_SCORE] and auto-applies a booking
     * block when the score drops below {@value BLOCK_BOOKING_BELOW}.
     */
    private void adjustScore(User user, int delta) {
        int newScore = Math.max(MIN_SCORE, Math.min(MAX_SCORE, user.getReliabilityScore() + delta));
        user.setReliabilityScore(newScore);

        if (newScore < BLOCK_BOOKING_BELOW) {
            Instant blockUntil = Instant.now().plus(BLOCK_DURATION_DAYS, ChronoUnit.DAYS);
            if (user.getBookingBlockedUntil() == null
                    || user.getBookingBlockedUntil().isBefore(blockUntil)) {
                user.setBookingBlockedUntil(blockUntil);
                log.warn("User {} auto-blocked until {} (score={})",
                        user.getId(), blockUntil.toString().substring(0, 10), newScore);
            }
        }
    }
}
