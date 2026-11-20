package com.looki.booking.service;

import com.looki.booking.entity.Appointment;
import com.looki.booking.entity.AppointmentStatus;
import com.looki.booking.repository.AppointmentRepository;
import com.looki.common.reliability.ReliabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Automatically flags appointments as NO_SHOW when they have passed without
 * being marked COMPLETED or CANCELLED by the salon owner.
 *
 * <p>Runs every 30 minutes. A 2-hour grace period after the appointment's
 * scheduled end time is applied before flagging, giving owners time to complete
 * or close bookings normally. Each flagged appointment triggers the standard
 * -20 reliability penalty on the client.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoShowAutoFlagService {

    /** Grace period after appointment end before auto-flagging as no-show. */
    private static final int GRACE_HOURS = 2;

    private final AppointmentRepository appointmentRepository;
    private final ReliabilityService reliabilityService;

    @Scheduled(fixedRate = 1_800_000) // every 30 minutes
    @Transactional
    public void flagMissedAppointments() {
        LocalDate today = LocalDate.now();
        LocalTime cutoff = LocalTime.now().minusHours(GRACE_HOURS);

        List<Appointment> missed = appointmentRepository
                .findMissedConfirmedAppointments(today, cutoff);

        if (missed.isEmpty()) return;

        log.info("Auto-flagging {} missed appointment(s) as NO_SHOW", missed.size());

        for (Appointment appt : missed) {
            appt.setStatus(AppointmentStatus.NO_SHOW);
            appointmentRepository.save(appt);

            if (appt.getUser() != null) {
                reliabilityService.applyNoShow(appt.getUser());
                log.info("NO_SHOW auto-flag: appointment={}, user={}, salon={}",
                        appt.getId(), appt.getUser().getId(),
                        appt.getSalon().getName());
            }
        }
    }
}
