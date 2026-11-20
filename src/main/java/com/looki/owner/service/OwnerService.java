package com.looki.owner.service;

import com.looki.auth.entity.User;
import com.looki.auth.repository.UserRepository;
import com.looki.booking.dto.BookingDto;
import com.looki.booking.entity.Appointment;
import com.looki.booking.entity.AppointmentStatus;
import com.looki.booking.entity.WaitlistEntry;
import com.looki.booking.entity.WaitlistStatus;
import com.looki.booking.repository.AppointmentRepository;
import com.looki.booking.repository.WaitlistRepository;
import com.looki.common.exception.BadRequestException;
import com.looki.common.exception.ResourceNotFoundException;
import com.looki.common.reliability.ReliabilityService;
import com.looki.common.reliability.ReliabilityTier;
import com.looki.owner.dto.*;
import com.looki.owner.entity.JoinRequest;
import com.looki.owner.entity.JoinRequest.JoinRequestStatus;
import com.looki.owner.repository.JoinRequestRepository;
import com.looki.common.exception.ConflictException;
import com.looki.salon.dto.CreatePortfolioPostRequest;
import com.looki.salon.dto.PortfolioPostDto;
import com.looki.salon.dto.SalonDto;
import com.looki.salon.dto.UpdatePortfolioPostRequest;
import com.looki.salon.dto.WorkingHoursDto;
import com.looki.salon.entity.PortfolioPost;
import com.looki.salon.entity.Salon;
import com.looki.salon.entity.SalonService;
import com.looki.salon.entity.WorkingHours;
import com.looki.salon.repository.PortfolioRepository;
import com.looki.salon.repository.SalonRepository;
import com.looki.salon.repository.WorkingHoursRepository;
import com.looki.salon.service.SalonServiceImpl;
import lombok.RequiredArgsConstructor;
import com.looki.booking.entity.AppointmentPaymentStatus;
import com.looki.booking.entity.PaymentMethod;
import com.looki.booking.event.AppointmentCancelledEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OwnerService {

    private final SalonRepository salonRepository;
    private final AppointmentRepository appointmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final SalonServiceImpl salonMapper;
    private final PortfolioRepository portfolioRepository;
    private final ReliabilityService reliabilityService;
    private final WorkingHoursRepository workingHoursRepository;
    private final JoinRequestRepository joinRequestRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ──────────────── Salon CRUD ────────────────

    @Transactional
    public SalonDto createSalon(UUID ownerId, CreateSalonRequest req) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));

        Salon salon = Salon.builder()
                .name(req.getName())
                .description(req.getDescription())
                .address(req.getAddress())
                .phone(req.getPhone())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .openingHours(req.getOpeningHours())
                .tags(req.getTags() != null ? req.getTags() : List.of())
                .owner(owner)
                .build();

        return salonMapper.toDto(salonRepository.save(salon));
    }

    @Transactional(readOnly = true)
    public List<SalonDto> getMySalons(UUID ownerId) {
        return salonRepository.findByOwnerId(ownerId).stream()
                .map(salonMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SalonDto getMySalon(UUID salonId, UUID ownerId) {
        return salonMapper.toDto(findOwned(salonId, ownerId));
    }

    @Transactional
    public SalonDto updateSalon(UUID salonId, UUID ownerId, UpdateSalonRequest req) {
        Salon salon = findOwned(salonId, ownerId);
        if (req.getName() != null)         salon.setName(req.getName());
        if (req.getDescription() != null)  salon.setDescription(req.getDescription());
        if (req.getAddress() != null)      salon.setAddress(req.getAddress());
        if (req.getPhone() != null)        salon.setPhone(req.getPhone());
        if (req.getLatitude() != 0)        salon.setLatitude(req.getLatitude());
        if (req.getLongitude() != 0)       salon.setLongitude(req.getLongitude());
        if (req.getOpeningHours() != null) salon.setOpeningHours(req.getOpeningHours());
        if (req.getTags() != null)         salon.setTags(req.getTags());
        return salonMapper.toDto(salonRepository.save(salon));
    }

    @Transactional
    public SalonDto uploadSalonImage(UUID salonId, UUID ownerId, String base64Image) {
        Salon salon = findOwned(salonId, ownerId);
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        salon.setImageData(imageBytes);
        // Clear any legacy external URL so the served-from-DB path takes precedence
        salon.setImageUrl(null);
        return salonMapper.toDto(salonRepository.save(salon));
    }

    @Transactional
    public void deleteSalon(UUID salonId, UUID ownerId) {
        Salon salon = findOwned(salonId, ownerId);
        salonRepository.delete(salon);
    }

    // ──────────────── Service CRUD ────────────────

    @Transactional
    public SalonDto addService(UUID salonId, UUID ownerId, CreateServiceRequest req) {
        Salon salon = findOwned(salonId, ownerId);
        SalonService service = SalonService.builder()
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .price(req.getPrice())
                .durationMinutes(req.getDurationMinutes())
                .icon(req.getIcon())
                .build();
        salon.addService(service);
        return salonMapper.toDto(salonRepository.save(salon));
    }

    @Transactional
    public SalonDto updateService(UUID salonId, UUID serviceId, UUID ownerId, UpdateServiceRequest req) {
        Salon salon = findOwned(salonId, ownerId);
        SalonService service = salon.getServices().stream()
                .filter(s -> s.getId().equals(serviceId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Service", "id", serviceId));

        if (req.getName() != null)            service.setName(req.getName());
        if (req.getDescription() != null)     service.setDescription(req.getDescription());
        if (req.getCategory() != null)        service.setCategory(req.getCategory());
        if (req.getPrice() != null)           service.setPrice(req.getPrice());
        if (req.getDurationMinutes() != null) service.setDurationMinutes(req.getDurationMinutes());
        if (req.getIcon() != null)            service.setIcon(req.getIcon());

        return salonMapper.toDto(salonRepository.save(salon));
    }

    @Transactional
    public SalonDto removeService(UUID salonId, UUID serviceId, UUID ownerId) {
        Salon salon = findOwned(salonId, ownerId);
        boolean removed = salon.getServices().removeIf(s -> s.getId().equals(serviceId));
        if (!removed) throw new ResourceNotFoundException("Service", "id", serviceId);
        return salonMapper.toDto(salonRepository.save(salon));
    }

    // ──────────────── Bookings ────────────────

    @Transactional(readOnly = true)
    public List<BookingDto> getUpcomingBookings(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return appointmentRepository.findUpcomingBySalon(salonId, LocalDate.now())
                .stream().map(this::toBookingDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getPastBookings(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return appointmentRepository.findPastBySalon(salonId, LocalDate.now())
                .stream().map(this::toBookingDto).toList();
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getAllBookings(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return appointmentRepository.findAllBySalonId(salonId)
                .stream().map(this::toBookingDto).toList();
    }

    /**
     * Confirm a PENDING booking -> CONFIRMED.
     * Checks for slot conflicts first (another booking may already be confirmed).
     */
    @Transactional
    public BookingDto confirmBooking(UUID bookingId, UUID ownerId) {
        Appointment appt = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        verifyOwnership(appt, ownerId);

        if (appt.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only PENDING bookings can be confirmed");
        }
        // Card bookings confirm themselves once Stripe reports the payment
        if (appt.getPaymentMethod() == PaymentMethod.PAY_WITH_CARD
                && appt.getPaymentStatus() == AppointmentPaymentStatus.AWAITING_PAYMENT) {
            throw new BadRequestException("This booking is waiting for the client's card payment");
        }

        // Guard: another booking may have already been confirmed for this slot
        List<Appointment> conflicts = appointmentRepository.findConflicts(
                appt.getSalon().getId(), appt.getDate(),
                appt.getStartTime(), appt.getEndTime());
        conflicts.removeIf(a -> a.getId().equals(bookingId));
        if (!conflicts.isEmpty()) {
            throw new BadRequestException(
                    "This time slot was already confirmed for another booking");
        }

        appt.setStatus(AppointmentStatus.CONFIRMED);
        BookingDto result = toBookingDto(appointmentRepository.save(appt));

        // Auto-cancel all other PENDING requests that overlap this now-confirmed slot
        List<Appointment> conflicting = appointmentRepository.findConflictingPending(
                appt.getSalon().getId(), appt.getDate(),
                appt.getStartTime(), appt.getEndTime(), appt.getId());
        for (Appointment conflict : conflicting) {
            conflict.setStatus(AppointmentStatus.CANCELLED);
        }
        if (!conflicting.isEmpty()) {
            appointmentRepository.saveAll(conflicting);
        }

        return result;
    }

    @Transactional
    public BookingDto completeBooking(UUID bookingId, UUID ownerId) {
        Appointment appt = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        verifyOwnership(appt, ownerId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new BadRequestException("Only CONFIRMED bookings can be completed");
        }
        appt.setStatus(AppointmentStatus.COMPLETED);
        // Reward client for completing the appointment
        User client = appt.getUser();
        if (client != null) {
            reliabilityService.applyCompletion(client);
        }
        return toBookingDto(appointmentRepository.save(appt));
    }

    /**
     * Mark a booking as no-show: the client did not attend.
     * Applies a -20 reliability penalty and sets status to NO_SHOW.
     */
    @Transactional
    public BookingDto markNoShow(UUID bookingId, UUID ownerId) {
        Appointment appt = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        verifyOwnership(appt, ownerId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED
                && appt.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only PENDING or CONFIRMED bookings can be marked as no-show");
        }
        appt.setStatus(AppointmentStatus.NO_SHOW);
        User client = appt.getUser();
        if (client != null) {
            reliabilityService.applyNoShow(client);
        }
        return toBookingDto(appointmentRepository.save(appt));
    }

    /**
     * Reject a booking — works on both PENDING and CONFIRMED.
     */
    @Transactional
    public BookingDto rejectBooking(UUID bookingId, UUID ownerId) {
        Appointment appt = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", "id", bookingId));
        verifyOwnership(appt, ownerId);
        if (appt.getStatus() != AppointmentStatus.CONFIRMED
                && appt.getStatus() != AppointmentStatus.PENDING) {
            throw new BadRequestException("Only PENDING or CONFIRMED bookings can be rejected");
        }
        boolean wasConfirmed = appt.getStatus() == AppointmentStatus.CONFIRMED;
        appt.setStatus(AppointmentStatus.CANCELLED);
        BookingDto result = toBookingDto(appointmentRepository.save(appt));
        // Refunds card payments once this transaction commits
        eventPublisher.publishEvent(new AppointmentCancelledEvent(appt.getId()));
        // Only notify waitlist when a CONFIRMED slot is freed — PENDING never blocked it
        if (wasConfirmed) {
            notifyFirstWaiting(appt.getSalon().getId(), appt.getDate());
        }
        return result;
    }

    // ──────────────── Portfolio ────────────────

    @Transactional(readOnly = true)
    public List<PortfolioPostDto> getPortfolioPosts(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return portfolioRepository.findBySalonIdOrderByCreatedAtDesc(salonId)
                .stream().map(this::toPortfolioDto).toList();
    }

    @Transactional
    public PortfolioPostDto addPortfolioPost(UUID salonId, UUID ownerId, CreatePortfolioPostRequest req) {
        Salon salon = findOwned(salonId, ownerId);
        byte[] imageBytes = Base64.getDecoder().decode(req.getImageData());
        PortfolioPost post = PortfolioPost.builder()
                .salon(salon)
                .caption(req.getCaption())
                .imageData(imageBytes)
                .build();
        return toPortfolioDto(portfolioRepository.save(post));
    }

    @Transactional
    public PortfolioPostDto updatePortfolioPost(UUID salonId, UUID postId, UUID ownerId, UpdatePortfolioPostRequest req) {
        findOwned(salonId, ownerId);
        PortfolioPost post = portfolioRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));
        if (!post.getSalon().getId().equals(salonId)) {
            throw new BadRequestException("Post does not belong to this salon");
        }
        post.setCaption(req.getCaption());
        if (req.getImageData() != null && !req.getImageData().isBlank()) {
            post.setImageData(Base64.getDecoder().decode(req.getImageData()));
        }
        return toPortfolioDto(portfolioRepository.save(post));
    }

    @Transactional
    public void deletePortfolioPost(UUID salonId, UUID postId, UUID ownerId) {
        findOwned(salonId, ownerId);
        PortfolioPost post = portfolioRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", "id", postId));
        if (!post.getSalon().getId().equals(salonId)) {
            throw new BadRequestException("Post does not belong to this salon");
        }
        portfolioRepository.delete(post);
    }

    private PortfolioPostDto toPortfolioDto(PortfolioPost p) {
        return PortfolioPostDto.builder()
                .id(p.getId().toString())
                .salonId(p.getSalon().getId().toString())
                .caption(p.getCaption())
                .imageUrl(p.getImageData() != null && p.getImageData().length > 0
                        ? "/api/portfolio/" + p.getId() + "/image"
                        : null)
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                .build();
    }

    // ──────────────── Dashboard ────────────────

    @Transactional(readOnly = true)
    public DashboardDto getDashboard(UUID salonId, UUID ownerId) {
        Salon salon = findOwned(salonId, ownerId);
        LocalDate today = LocalDate.now();

        List<Appointment> todayAppts = appointmentRepository.findTodayBySalon(salonId, today);
        List<Appointment> upcoming = appointmentRepository.findUpcomingBySalon(salonId, today);
        List<Appointment> all = appointmentRepository.findAllBySalonId(salonId);

        long completed = all.stream().filter(a -> a.getStatus() == AppointmentStatus.COMPLETED).count();
        long cancelled = all.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();

        // Today's revenue: CONFIRMED + COMPLETED so it persists after completion
        double todayRevenue = todayAppts.stream()
                .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED
                          || a.getStatus() == AppointmentStatus.COMPLETED)
                .mapToDouble(Appointment::getTotalPrice).sum();

        // Monthly revenue: only COMPLETED appointments in the current month
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        List<Appointment> monthlyCompleted = appointmentRepository.findCompletedInMonth(salonId, monthStart, monthEnd);
        double totalRevenue = monthlyCompleted.stream()
                .mapToDouble(Appointment::getTotalPrice).sum();

        return DashboardDto.builder()
                .salonId(salon.getId().toString())
                .salonName(salon.getName())
                .todayAppointments(todayAppts.size())
                .upcomingAppointments(upcoming.size())
                .completedAppointments((int) completed)
                .cancelledAppointments((int) cancelled)
                .todayRevenue(todayRevenue)
                .totalRevenue(totalRevenue)
                .todayBookings(todayAppts.stream().map(this::toBookingDto).toList())
                .build();
    }

    // ──────────────── Working Hours ────────────────

    @Transactional(readOnly = true)
    public List<WorkingHoursDto> getWorkingHours(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return workingHoursRepository.findBySalonIdOrderByDayOfWeek(salonId)
                .stream().map(this::toWorkingHoursDto).toList();
    }

    @Transactional
    public List<WorkingHoursDto> saveWorkingHours(UUID salonId, UUID ownerId, List<WorkingHoursDto> dtos) {
        Salon salon = findOwned(salonId, ownerId);
        workingHoursRepository.deleteBySalonId(salonId);
        List<WorkingHours> hours = dtos.stream().map(dto -> WorkingHours.builder()
                .salon(salon)
                .dayOfWeek(dto.getDayOfWeek())
                .openTime(dto.getOpenTime())
                .closeTime(dto.getCloseTime())
                .closed(dto.isClosed())
                .build()).toList();
        return workingHoursRepository.saveAll(hours).stream()
                .sorted(java.util.Comparator.comparingInt(WorkingHours::getDayOfWeek))
                .map(this::toWorkingHoursDto).toList();
    }

    private WorkingHoursDto toWorkingHoursDto(WorkingHours wh) {
        return WorkingHoursDto.builder()
                .dayOfWeek(wh.getDayOfWeek())
                .openTime(wh.getOpenTime())
                .closeTime(wh.getCloseTime())
                .closed(wh.isClosed())
                .build();
    }

    // ──────────────── Join Requests ────────────────

    @Transactional
    public JoinRequestDto submitJoinRequest(UUID salonId, UUID userId, CreateJoinRequestRequest req) {
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "id", salonId));

        if (joinRequestRepository.existsBySalonIdAndUserIdAndStatus(salonId, userId, JoinRequestStatus.PENDING)) {
            throw new ConflictException("You already have a pending join request for this salon");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        JoinRequest joinRequest = JoinRequest.builder()
                .salon(salon)
                .userId(userId)
                .userName(user.getFullName())
                .userEmail(user.getEmail())
                .message(req.getMessage())
                .build();

        return toJoinRequestDto(joinRequestRepository.save(joinRequest));
    }

    @Transactional(readOnly = true)
    public List<JoinRequestDto> getPendingJoinRequests(UUID salonId, UUID ownerId) {
        findOwned(salonId, ownerId);
        return joinRequestRepository
                .findBySalonIdAndStatusOrderByCreatedAtDesc(salonId, JoinRequestStatus.PENDING)
                .stream().map(this::toJoinRequestDto).toList();
    }

    @Transactional
    public JoinRequestDto approveJoinRequest(UUID salonId, UUID requestId, UUID ownerId) {
        findOwned(salonId, ownerId);
        JoinRequest request = joinRequestRepository.findByIdAndSalonId(requestId, salonId)
                .orElseThrow(() -> new ResourceNotFoundException("JoinRequest", "id", requestId));

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("This join request has already been resolved");
        }

        request.setStatus(JoinRequestStatus.APPROVED);
        request.setResolvedAt(java.time.Instant.now());
        return toJoinRequestDto(joinRequestRepository.save(request));
    }

    @Transactional
    public void rejectJoinRequest(UUID salonId, UUID requestId, UUID ownerId) {
        findOwned(salonId, ownerId);
        JoinRequest request = joinRequestRepository.findByIdAndSalonId(requestId, salonId)
                .orElseThrow(() -> new ResourceNotFoundException("JoinRequest", "id", requestId));

        if (request.getStatus() != JoinRequestStatus.PENDING) {
            throw new BadRequestException("This join request has already been resolved");
        }

        request.setStatus(JoinRequestStatus.REJECTED);
        request.setResolvedAt(java.time.Instant.now());
        joinRequestRepository.save(request);
    }

    private JoinRequestDto toJoinRequestDto(JoinRequest r) {
        return JoinRequestDto.builder()
                .id(r.getId().toString())
                .salonId(r.getSalon().getId().toString())
                .userId(r.getUserId().toString())
                .userName(r.getUserName())
                .userEmail(r.getUserEmail())
                .message(r.getMessage())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null)
                .resolvedAt(r.getResolvedAt() != null ? r.getResolvedAt().toString() : null)
                .build();
    }

    private void notifyFirstWaiting(UUID salonId, java.time.LocalDate date) {
        List<WaitlistEntry> waiting = waitlistRepository
                .findBySalonIdAndDateAndStatusOrderByCreatedAtAsc(salonId, date, WaitlistStatus.WAITING);
        if (!waiting.isEmpty()) {
            waiting.get(0).setStatus(WaitlistStatus.NOTIFIED);
            waitlistRepository.save(waiting.get(0));
        }
    }

    // ──────────────── Helpers ────────────────

    private Salon findOwned(UUID salonId, UUID ownerId) {
        return salonRepository.findByIdAndOwnerId(salonId, ownerId)
                .orElseThrow(() -> new BadRequestException("Salon not found or you don't own it"));
    }

    private void verifyOwnership(Appointment appt, UUID ownerId) {
        UUID salonOwnerId = appt.getSalon().getOwner() != null
                ? appt.getSalon().getOwner().getId() : null;
        if (!ownerId.equals(salonOwnerId)) {
            throw new BadRequestException("You don't own this salon");
        }
    }

    private BookingDto toBookingDto(Appointment a) {
        User client = a.getUser();
        int clientScore = client != null ? client.getReliabilityScore() : 100;
        return BookingDto.builder()
                .id(a.getId().toString())
                .salonId(a.getSalon().getId().toString())
                .salonName(a.getSalon().getName())
                .salonAddress(a.getSalon().getAddress())
                .salonImageUrl(a.getSalon().getImageData() != null && a.getSalon().getImageData().length > 0
                        ? "/api/salons/" + a.getSalon().getId() + "/image"
                        : null)
                .date(a.getDate())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus().name())
                .paymentMethod(a.getPaymentMethod().name())
                .paymentStatus(a.getPaymentStatus() != null ? a.getPaymentStatus().name() : null)
                .notes(a.getNotes())
                .totalPrice(a.getTotalPrice())
                .requiresClientConfirmation(a.isRequiresClientConfirmation())
                .clientConfirmedAt(a.getClientConfirmedAt() != null
                        ? a.getClientConfirmedAt().toString() : null)
                .clientId(client != null ? client.getId().toString() : null)
                .clientName(client != null ? client.getFullName() : null)
                .clientAvatarUrl(client != null && client.getAvatarData() != null && client.getAvatarData().length > 0
                        ? "/api/auth/users/" + client.getId() + "/avatar"
                        : null)
                .clientReliabilityScore(clientScore)
                .clientReliabilityTier(ReliabilityTier.fromScore(clientScore).name())
                .services(a.getServices().stream().map(s ->
                        BookingDto.BookingServiceDto.builder()
                                .serviceName(s.getServiceName())
                                .price(s.getPrice())
                                .durationMinutes(s.getDurationMinutes())
                                .build()
                ).toList())
                .build();
    }
}
