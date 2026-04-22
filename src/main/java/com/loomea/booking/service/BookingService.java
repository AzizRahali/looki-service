package com.loomea.booking.service;

import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import com.loomea.booking.dto.*;
import com.loomea.booking.entity.*;
import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.common.reliability.ReliabilityService;
import com.loomea.common.reliability.ReliabilityTier;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.SalonService;
import com.loomea.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private static final LocalTime OPEN  = LocalTime.of(9, 0);
    private static final LocalTime CLOSE = LocalTime.of(20, 0);
    private static final int SLOT_STEP   = 30;

    private final AppointmentRepository appointmentRepository;
    private final SalonRepository salonRepository;
    private final UserRepository userRepository;
    private final ReliabilityService reliabilityService;

    /**
     * Atomic booking creation.
     * Uses countConflicts (lightweight COUNT query) + @Version optimistic lock
     * to prevent double-booking under concurrent requests.
     */
    @Transactional
    public BookingDto create(UUID userId, CreateBookingRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // ── Reliability gate ──────────────────────────────────────────────────
        String blockReason = reliabilityService.getBlockReason(user);
        if (blockReason != null) {
            throw new BadRequestException(blockReason);
        }

        long activeBookings = appointmentRepository.countActiveByUserId(userId);
        int maxAllowed = reliabilityService.getMaxActiveBookings(user);
        if (activeBookings >= maxAllowed) {
            throw new BadRequestException(
                    "You have reached your active booking limit (" + maxAllowed + "). "
                    + "Complete or cancel an existing appointment first.");
        }
        // ─────────────────────────────────────────────────────────────────────

        Salon salon = salonRepository.findById(UUID.fromString(req.getSalonId()))
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "id", req.getSalonId()));

        if (req.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot book in the past");
        }

        // Rule: one active appointment per client per SALON per day
        List<Appointment> existingToday = appointmentRepository
                .findActiveByUserAndSalonAndDate(userId, salon.getId(), req.getDate());
        if (!existingToday.isEmpty()) {
            throw new BadRequestException("You already have an appointment at this salon on this day");
        }

        // Resolve selected services
        List<SalonService> selected = new ArrayList<>();
        for (String sid : req.getServiceIds()) {
            UUID serviceId = UUID.fromString(sid);
            SalonService svc = salon.getServices().stream()
                    .filter(s -> s.getId().equals(serviceId))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Service", "id", serviceId));
            selected.add(svc);
        }

        int totalDuration = selected.stream().mapToInt(SalonService::getDurationMinutes).sum();
        double totalPrice = selected.stream().mapToDouble(SalonService::getPrice).sum();
        LocalTime endTime = req.getStartTime().plusMinutes(totalDuration);

        // ATOMIC: COUNT-based conflict check — lightweight, no entity loading
        long conflicts = appointmentRepository.countConflicts(
                salon.getId(), req.getDate(), req.getStartTime(), endTime);
        if (conflicts > 0) {
            throw new BadRequestException("This time slot is already taken");
        }

        PaymentMethod paymentMethod = "PAY_WITH_CARD".equals(req.getPaymentMethod())
                ? PaymentMethod.PAY_WITH_CARD : PaymentMethod.PAY_AT_SALON;

        boolean needsConfirmation = reliabilityService.requiresConfirmation(user);

        Appointment appt = Appointment.builder()
                .user(user).salon(salon)
                .date(req.getDate()).startTime(req.getStartTime()).endTime(endTime)
                .paymentMethod(paymentMethod).notes(req.getNotes())
                .totalPrice(totalPrice)
                .requiresClientConfirmation(needsConfirmation)
                .build();

        for (SalonService svc : selected) {
            appt.addService(AppointmentService.builder()
                    .serviceName(svc.getName())
                    .price(svc.getPrice())
                    .durationMinutes(svc.getDurationMinutes())
                    .build());
        }

        try {
            return toDto(appointmentRepository.save(appt));
        } catch (OptimisticLockingFailureException e) {
            // Concurrent booking won the race
            throw new BadRequestException("This time slot was just booked by another user. Please try a different time.");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUpcoming(UUID userId) {
        return appointmentRepository.findUpcoming(userId, LocalDate.now())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getPast(UUID userId) {
        return appointmentRepository.findPast(userId, LocalDate.now())
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public BookingDto getById(UUID bookingId, UUID userId) {
        Appointment appt = findAndAuthorize(bookingId, userId);
        return toDto(appt);
    }

    @Transactional
    public BookingDto cancel(UUID bookingId, UUID userId) {
        Appointment appt = findAndAuthorize(bookingId, userId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED
                && appt.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only pending or confirmed appointments can be cancelled");
        }

        // ── Late cancellation penalty ─────────────────────────────────────────
        LocalDateTime appointmentStart = LocalDateTime.of(appt.getDate(), appt.getStartTime());
        boolean isLate = LocalDateTime.now()
                .plusHours(ReliabilityService.LATE_CANCEL_HOURS)
                .isAfter(appointmentStart);
        if (isLate) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
            reliabilityService.applyLateCancellation(user);
        }
        // ─────────────────────────────────────────────────────────────────────

        appt.setStatus(AppointmentStatus.CANCELLED);
        return toDto(appointmentRepository.save(appt));
    }

    /** Client explicitly confirms they will attend (required for risky-tier users). */
    @Transactional
    public BookingDto confirmAttendance(UUID bookingId, UUID userId) {
        Appointment appt = findAndAuthorize(bookingId, userId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException("Only confirmed appointments can have attendance confirmed");
        }
        if (!appt.isRequiresClientConfirmation()) {
            throw new BadRequestException("This appointment does not require attendance confirmation");
        }
        if (appt.getClientConfirmedAt() != null) {
            throw new BadRequestException("Attendance already confirmed");
        }
        appt.setClientConfirmedAt(Instant.now());
        return toDto(appointmentRepository.save(appt));
    }

    @Transactional
    public BookingDto reschedule(UUID bookingId, UUID userId, RescheduleRequest req) {
        Appointment appt = findAndAuthorize(bookingId, userId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED
                && appt.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only pending or confirmed appointments can be rescheduled");
        }
        if (req.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot reschedule to a past date");
        }

        int duration = appt.getServices().stream()
                .mapToInt(AppointmentService::getDurationMinutes).sum();
        LocalTime newEnd = req.getStartTime().plusMinutes(duration);

        // Atomic conflict check excluding self
        List<Appointment> conflicts = appointmentRepository.findConflicts(
                appt.getSalon().getId(), req.getDate(), req.getStartTime(), newEnd);
        conflicts.removeIf(a -> a.getId().equals(bookingId));
        if (!conflicts.isEmpty()) {
            throw new BadRequestException("This time slot is already taken");
        }

        appt.setDate(req.getDate());
        appt.setStartTime(req.getStartTime());
        appt.setEndTime(newEnd);
        appt.setStatus(AppointmentStatus.PENDING);
        return toDto(appointmentRepository.save(appt));
    }

    /**
     * Returns available time slots for a salon on a given date.
     * PENDING + CONFIRMED bookings make a slot unavailable (FCFS).
     */
    @Transactional(readOnly = true)
    public List<TimeSlotDto> getSlots(UUID salonId, LocalDate date, int durationMinutes) {
        List<Appointment> active = appointmentRepository.findActiveBySalonAndDate(salonId, date);
        List<TimeSlotDto> slots = new ArrayList<>();
        LocalTime current = OPEN;

        while (!current.plusMinutes(durationMinutes).isAfter(CLOSE)) {
            final LocalTime slotStart = current;
            LocalTime slotEnd = current.plusMinutes(durationMinutes);

            boolean taken = active.stream()
                    .anyMatch(a -> a.getStartTime().isBefore(slotEnd)
                            && a.getEndTime().isAfter(slotStart));

            // Skip slots that have already started or start within 30 minutes (today only)
            if (date.equals(LocalDate.now()) && current.isBefore(LocalTime.now().plusMinutes(30))) {
                current = current.plusMinutes(SLOT_STEP);
                continue;
            }

            String status = taken ? "BOOKED" : "AVAILABLE";
            slots.add(TimeSlotDto.builder().time(current).status(status).build());
            current = current.plusMinutes(SLOT_STEP);
        }
        return slots;
    }

    // ---- helpers ----

    private Appointment findAndAuthorize(UUID bookingId, UUID userId) {
        Appointment appt = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", bookingId));
        if (!appt.getUser().getId().equals(userId)) {
            throw new BadRequestException("Not authorized");
        }
        return appt;
    }

    private BookingDto toDto(Appointment a) {
        return BookingDto.builder()
                .id(a.getId().toString())
                .salonId(a.getSalon().getId().toString())
                .salonName(a.getSalon().getName())
                .salonAddress(a.getSalon().getAddress())
                .salonImageUrl(a.getSalon().getImageUrl())
                .date(a.getDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus().name())
                .paymentMethod(a.getPaymentMethod().name())
                .notes(a.getNotes())
                .totalPrice(a.getTotalPrice())
                .requiresClientConfirmation(a.isRequiresClientConfirmation())
                .clientConfirmedAt(a.getClientConfirmedAt() != null
                        ? a.getClientConfirmedAt().toString() : null)
                .services(a.getServices().stream()
                        .map(s -> BookingDto.BookingServiceDto.builder()
                                .serviceName(s.getServiceName())
                                .price(s.getPrice())
                                .durationMinutes(s.getDurationMinutes())
                                .build())
                        .toList())
                .build();
    }
}
