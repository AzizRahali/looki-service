package com.looki.booking.service;

import com.looki.booking.entity.Appointment;
import com.looki.booking.entity.AppointmentStatus;
import com.looki.booking.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Enforces attendance confirmation for risky-tier users.
 *
 * <p>Every 15 minutes this service scans for CONFIRMED appointments that:
 * <ul>
 *   <li>Require client confirmation ({@code requiresClientConfirmation=true})</li>
 *   <li>Have not yet been confirmed ({@code clientConfirmedAt IS NULL})</li>
 *   <li>Start within the next 3 hours (on today's date)</li>
 * </ul>
 * Those appointments are automatically cancelled — the slot is released and the
 * salon is protected from unannounced no-shows.</p>
 *
 * <p>The auto-cancellation does NOT apply an additional score penalty because the
 * user's score was already low enough to trigger the confirmation requirement in
 * the first place. The penalty will apply if the owner marks the missed slot as
 * a no-show instead.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfirmationReminderService {

    /** How many hours before appointment start is the confirmation window. */
    private static final int CONFIRMATION_WINDOW_HOURS = 3;

    private final AppointmentRepository appointmentRepository;

    @Scheduled(fixedRate = 900_000) // every 15 minutes
    @Transactional
    public void cancelUnconfirmedAppointments() {
        LocalDate today    = LocalDate.now();
        LocalTime now      = LocalTime.now();
        LocalTime deadline = now.plusHours(CONFIRMATION_WINDOW_HOURS);

        List<Appointment> unconfirmed = appointmentRepository
                .findUnconfirmedInWindow(today, now, deadline);

        for (Appointment appt : unconfirmed) {
            appt.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appt);
            log.warn("Auto-cancelled appointment {} — user {} did not confirm attendance within {}h window",
                    appt.getId(), appt.getUser().getId(), CONFIRMATION_WINDOW_HOURS);
        }

        if (!unconfirmed.isEmpty()) {
            log.info("Confirmation sweep: auto-cancelled {} appointment(s)", unconfirmed.size());
        }
    }
}
