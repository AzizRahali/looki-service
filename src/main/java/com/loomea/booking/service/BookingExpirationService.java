package com.loomea.booking.service;

import com.loomea.booking.entity.AppointmentStatus;
import com.loomea.booking.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Expires PENDING bookings that haven't been confirmed within the timeout.
 * Runs every 2 minutes. Releases held slots back to availability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationService {

    private static final long PENDING_TIMEOUT_MINUTES = 15;

    private final AppointmentRepository appointmentRepository;

    @Scheduled(fixedRate = 120_000) // every 2 minutes
    @Transactional
    public void expireStalePendingBookings() {
        Instant cutoff = Instant.now().minus(PENDING_TIMEOUT_MINUTES, ChronoUnit.MINUTES);
        int expired = appointmentRepository.expirePendingBefore(cutoff, AppointmentStatus.CANCELLED);
        if (expired > 0) {
            log.info("Expired {} stale PENDING bookings (older than {} min)", expired, PENDING_TIMEOUT_MINUTES);
        }
    }
}
