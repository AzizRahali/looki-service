package com.looki.payment.service;

import com.looki.auth.entity.Role;
import com.looki.auth.entity.User;
import com.looki.auth.repository.UserRepository;
import com.looki.booking.dto.BookingDto;
import com.looki.booking.dto.CreateBookingRequest;
import com.looki.booking.entity.Appointment;
import com.looki.booking.entity.AppointmentPaymentStatus;
import com.looki.booking.entity.AppointmentStatus;
import com.looki.booking.repository.AppointmentRepository;
import com.looki.booking.service.BookingService;
import com.looki.common.exception.BadRequestException;
import com.looki.common.exception.ResourceNotFoundException;
import com.looki.payment.config.StripeConfig;
import com.looki.payment.dto.InitiatePaymentRequest;
import com.looki.payment.dto.InitiatePaymentResponse;
import com.looki.payment.dto.PaymentHistoryItem;
import com.looki.payment.dto.PaymentStatusResponse;
import com.looki.payment.entity.PaymentRecord;
import com.looki.payment.entity.PaymentRecordStatus;
import com.looki.payment.entity.PaymentType;
import com.looki.payment.repository.PaymentRepository;
import com.looki.subscription.entity.Subscription;
import com.looki.subscription.entity.SubscriptionPlan;
import com.looki.subscription.entity.SubscriptionStatus;
import com.looki.subscription.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    /** Stripe requires Checkout sessions to stay open for at least 30 minutes. */
    private static final long CHECKOUT_TTL_MINUTES = 31;

    private final StripeConfig stripeConfig;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final AppointmentRepository appointmentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe Checkout Session and returns its hosted payment URL.
     * For BOOKING this also creates the appointment as PENDING / AWAITING_PAYMENT,
     * in the same transaction — if Stripe fails, the booking is rolled back.
     *
     * @param returnBaseUrl backend base URL used to build the default return pages
     */
    @Transactional
    public InitiatePaymentResponse initiate(InitiatePaymentRequest req, UUID userId, String returnBaseUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (req.getType() == null) {
            throw new BadRequestException("Payment type is required");
        }

        return switch (req.getType().toUpperCase(Locale.ROOT)) {
            case "BOOKING"      -> initiateBookingPayment(req, user, returnBaseUrl);
            case "SUBSCRIPTION" -> initiateSubscriptionPayment(req, user, returnBaseUrl);
            default -> throw new BadRequestException("Unknown payment type: " + req.getType());
        };
    }

    /**
     * Called by the Stripe webhook (POST /api/payments/webhook).
     * Verifies the Stripe-Signature header, then fulfils or cancels the payment.
     * The controller always returns 200 — exceptions are swallowed there.
     */
    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        String secret = stripeConfig.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("STRIPE_WEBHOOK_SECRET not configured — webhook ignored");
            return;
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, secret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature invalid: {}", e.getMessage());
            return;
        }

        String type = event.getType();
        if (!"checkout.session.completed".equals(type) && !"checkout.session.expired".equals(type)) return;

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (session == null) return;

        syncSession(session.getId());
    }

    /**
     * Re-checks a session with Stripe and fulfils or cancels it. Safe to call
     * from public endpoints: it only acts on what Stripe reports.
     */
    @Transactional
    public void syncSession(String sessionId) {
        paymentRepository.findForUpdate(sessionId).ifPresentOrElse(record -> {
            if (record.getStatus() == PaymentRecordStatus.PENDING) {
                verifyAndFulfil(record);
            }
        }, () -> log.warn("No PaymentRecord for Stripe session {}", sessionId));
    }

    /**
     * Polled by Flutter every few seconds.
     * If still PENDING, asks Stripe for the latest state and fulfils inline —
     * so polling works even without a configured webhook secret (local dev, staging).
     */
    @Transactional
    public PaymentStatusResponse getStatus(String sessionId, UUID userId) {
        PaymentRecord record = lockOwnRecord(sessionId, userId);
        if (record.getStatus() == PaymentRecordStatus.PENDING) {
            verifyAndFulfil(record);
        }
        return toStatus(record);
    }

    /**
     * The client backed out of Checkout. Expires the session so it can no
     * longer be paid, and frees the booking's slot right away instead of
     * waiting for the expiration job.
     */
    @Transactional
    public PaymentStatusResponse cancel(String sessionId, UUID userId) {
        PaymentRecord record = lockOwnRecord(sessionId, userId);
        if (record.getStatus() != PaymentRecordStatus.PENDING) {
            return toStatus(record);
        }

        try {
            Session session = Session.retrieve(sessionId);
            if ("paid".equals(session.getPaymentStatus())) {
                // Paid in the meantime — honour the payment instead of cancelling
                fulfilPaid(record, session);
                return toStatus(record);
            }
            if ("open".equals(session.getStatus())) {
                session.expire();
            }
        } catch (StripeException e) {
            log.error("Stripe error expiring session {}: {}", sessionId, e.getMessage());
            throw new BadRequestException("Could not cancel the payment, please try again");
        }

        markCancelled(record);
        return toStatus(record);
    }

    @Transactional(readOnly = true)
    public List<PaymentHistoryItem> getHistory(UUID userId) {
        return paymentRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                        EnumSet.of(PaymentRecordStatus.COMPLETED,
                                PaymentRecordStatus.REFUNDED,
                                PaymentRecordStatus.FAILED))
                .stream()
                .map(this::toHistoryItem)
                .toList();
    }

    /**
     * Called after an appointment is cancelled or rejected (see
     * {@link BookingRefundListener}). Refunds a completed card payment and
     * expires any Checkout still open for it, so it can't be paid later.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refundBookingIfPaid(UUID appointmentId) {
        for (PaymentRecord found : paymentRepository.findByTypeAndReferenceId(
                PaymentType.BOOKING, appointmentId.toString())) {
            PaymentRecord record = paymentRepository.findForUpdate(found.getPaymentRef()).orElse(found);

            if (record.getStatus() == PaymentRecordStatus.COMPLETED) {
                refund(record);
            } else if (record.getStatus() == PaymentRecordStatus.PENDING) {
                try {
                    Session session = Session.retrieve(record.getPaymentRef());
                    if ("paid".equals(session.getPaymentStatus())) {
                        record.setPaymentIntentId(session.getPaymentIntent());
                        refund(record);
                    } else {
                        if ("open".equals(session.getStatus())) session.expire();
                        record.setStatus(PaymentRecordStatus.CANCELLED);
                    }
                } catch (StripeException e) {
                    log.error("Stripe error closing session {} for cancelled appointment {}: {}",
                            record.getPaymentRef(), appointmentId, e.getMessage());
                }
            }
            paymentRepository.save(record);
        }
    }

    // ── Shared fulfillment ─────────────────────────────────────────────────────

    private void verifyAndFulfil(PaymentRecord record) {
        Session session;
        try {
            session = Session.retrieve(record.getPaymentRef());
        } catch (StripeException e) {
            log.error("Stripe API error verifying session {}: {}", record.getPaymentRef(), e.getMessage());
            return;
        }

        if ("paid".equals(session.getPaymentStatus())) {
            fulfilPaid(record, session);
        } else if ("expired".equals(session.getStatus())) {
            markCancelled(record);
        }
    }

    private void fulfilPaid(PaymentRecord record, Session session) {
        record.setPaymentIntentId(session.getPaymentIntent());
        try {
            if (record.getType() == PaymentType.BOOKING) {
                if (fulfillBooking(record)) {
                    record.setStatus(PaymentRecordStatus.COMPLETED);
                } else {
                    // Booking was cancelled/expired/taken before the money arrived
                    refund(record);
                }
            } else {
                fulfillSubscription(record);
                record.setStatus(PaymentRecordStatus.COMPLETED);
            }
        } catch (Exception e) {
            log.error("Error fulfilling payment {}: {}", record.getPaymentRef(), e.getMessage(), e);
            record.setStatus(PaymentRecordStatus.FAILED);
            if (record.getType() == PaymentType.BOOKING) refund(record);
        }
        paymentRepository.save(record);
    }

    private void markCancelled(PaymentRecord record) {
        record.setStatus(PaymentRecordStatus.CANCELLED);
        paymentRepository.save(record);

        if (record.getType() != PaymentType.BOOKING) return;
        appointmentRepository.findById(UUID.fromString(record.getReferenceId())).ifPresent(appt -> {
            if (appt.getStatus() == AppointmentStatus.PENDING
                    && appt.getPaymentStatus() == AppointmentPaymentStatus.AWAITING_PAYMENT) {
                appt.setStatus(AppointmentStatus.CANCELLED);
                appointmentRepository.save(appt);
                log.info("Appointment {} cancelled — checkout {} not paid", appt.getId(), record.getPaymentRef());
            }
        });
    }

    /**
     * Refunds the full amount. On failure the record is left as is and the
     * error logged, so it can be refunded from the Stripe dashboard.
     */
    private void refund(PaymentRecord record) {
        try {
            String paymentIntent = record.getPaymentIntentId();
            if (paymentIntent == null) {
                paymentIntent = Session.retrieve(record.getPaymentRef()).getPaymentIntent();
                record.setPaymentIntentId(paymentIntent);
            }
            Refund.create(
                    RefundCreateParams.builder().setPaymentIntent(paymentIntent).build(),
                    RequestOptions.builder().setIdempotencyKey("refund-" + record.getPaymentRef()).build());
            record.setStatus(PaymentRecordStatus.REFUNDED);
            log.info("Refunded Stripe payment {}", record.getPaymentRef());

            if (record.getType() == PaymentType.BOOKING) {
                appointmentRepository.findById(UUID.fromString(record.getReferenceId())).ifPresent(appt -> {
                    appt.setPaymentStatus(AppointmentPaymentStatus.REFUNDED);
                    appointmentRepository.save(appt);
                });
            }
        } catch (StripeException e) {
            log.error("REFUND FAILED for Stripe payment {} — refund it manually in the Stripe dashboard: {}",
                    record.getPaymentRef(), e.getMessage());
        }
    }

    // ── BOOKING ────────────────────────────────────────────────────────────────

    private InitiatePaymentResponse initiateBookingPayment(InitiatePaymentRequest req, User user, String baseUrl) {
        if (req.getSalonId() == null || req.getDate() == null ||
                req.getStartTime() == null || req.getServiceIds() == null || req.getServiceIds().isEmpty()) {
            throw new BadRequestException("salonId, date, startTime and serviceIds are required for BOOKING");
        }

        LocalDate date;
        LocalTime startTime;
        try {
            date = LocalDate.parse(req.getDate());
            startTime = LocalTime.parse(req.getStartTime());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date or startTime format");
        }

        CreateBookingRequest bookingReq = new CreateBookingRequest();
        bookingReq.setSalonId(req.getSalonId());
        bookingReq.setDate(date);
        bookingReq.setStartTime(startTime);
        bookingReq.setServiceIds(req.getServiceIds());
        bookingReq.setPaymentMethod("PAY_WITH_CARD");
        bookingReq.setNotes(req.getNotes());

        BookingDto booking = bookingService.create(user.getId(), bookingReq);
        UUID appointmentId = UUID.fromString(booking.getId());

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));
        appt.setPaymentStatus(AppointmentPaymentStatus.AWAITING_PAYMENT);
        appointmentRepository.save(appt);

        String services = booking.getServices() == null ? "" : booking.getServices().stream()
                .map(BookingDto.BookingServiceDto::getServiceName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String description = services + " · " + date + " " + startTime;

        return createCheckout(user, PaymentType.BOOKING, appointmentId.toString(),
                booking.getTotalPrice(), "Booking at " + booking.getSalonName(), description, baseUrl);
    }

    /** @return false if the appointment can no longer be confirmed and must be refunded */
    private boolean fulfillBooking(PaymentRecord record) {
        UUID appointmentId = UUID.fromString(record.getReferenceId());
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (appt.getStatus() == AppointmentStatus.CONFIRMED
                && appt.getPaymentStatus() == AppointmentPaymentStatus.PAID) {
            return true; // already fulfilled
        }
        if (appt.getStatus() != AppointmentStatus.PENDING) {
            log.warn("Appointment {} is {} — refunding payment {}",
                    appointmentId, appt.getStatus(), record.getPaymentRef());
            return false;
        }

        // Same guard as owner confirmation: never double-book a slot
        List<Appointment> conflicts = appointmentRepository.findConflicts(
                appt.getSalon().getId(), appt.getDate(), appt.getStartTime(), appt.getEndTime());
        conflicts.removeIf(a -> a.getId().equals(appointmentId));
        if (!conflicts.isEmpty()) {
            appt.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appt);
            log.warn("Slot for appointment {} was taken — refunding payment {}",
                    appointmentId, record.getPaymentRef());
            return false;
        }

        appt.setStatus(AppointmentStatus.CONFIRMED);
        appt.setPaymentStatus(AppointmentPaymentStatus.PAID);
        appointmentRepository.save(appt);

        List<Appointment> overlapping = appointmentRepository.findConflictingPending(
                appt.getSalon().getId(), appt.getDate(), appt.getStartTime(), appt.getEndTime(), appointmentId);
        overlapping.forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));
        if (!overlapping.isEmpty()) appointmentRepository.saveAll(overlapping);

        log.info("Appointment {} confirmed via Stripe session {}", appointmentId, record.getPaymentRef());
        return true;
    }

    // ── SUBSCRIPTION ───────────────────────────────────────────────────────────

    private InitiatePaymentResponse initiateSubscriptionPayment(InitiatePaymentRequest req, User user, String baseUrl) {
        if (user.getRole() != Role.SALON_OWNER) {
            throw new BadRequestException("Only salon owners can subscribe");
        }
        if (req.getPlan() == null) {
            throw new BadRequestException("plan is required for SUBSCRIPTION");
        }

        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.valueOf(req.getPlan().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown subscription plan: " + req.getPlan());
        }

        return createCheckout(user, PaymentType.SUBSCRIPTION, plan.name(), plan.getPrice(),
                "looki " + plan.getLabel() + " subscription",
                plan.getDurationDays() + " days of salon owner features", baseUrl);
    }

    /** Renewing early extends the current period instead of discarding the unused days. */
    private void fulfillSubscription(PaymentRecord record) {
        UUID userId = record.getUserId();
        SubscriptionPlan plan = SubscriptionPlan.valueOf(record.getReferenceId());
        Instant now = Instant.now();

        Optional<Subscription> active = subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE);
        Instant periodBase = active.map(Subscription::getEndDate)
                .filter(end -> end.isAfter(now))
                .orElse(now);
        active.ifPresent(existing -> {
            existing.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(existing);
        });

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(now)
                .endDate(periodBase.plus(plan.getDurationDays(), ChronoUnit.DAYS))
                .paymentReference(record.getPaymentRef())
                .build();

        subscriptionRepository.save(sub);
        log.info("Subscription activated for user {} plan {} via Stripe session {}",
                userId, plan, record.getPaymentRef());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private InitiatePaymentResponse createCheckout(User user, PaymentType type, String referenceId,
                                                   double amount, String productName,
                                                   String productDescription, String baseUrl) {
        String currency = stripeConfig.getCurrency().toLowerCase(Locale.ROOT);
        long amountMinor = CurrencyAmounts.toMinorUnits(amount * stripeConfig.getConversionRate(), currency);
        if (amountMinor <= 0) {
            throw new BadRequestException("Nothing to pay");
        }

        String returnUrl = baseUrl + "/api/payments/return";
        String successUrl = notBlank(stripeConfig.getSuccessUrl())
                ? stripeConfig.getSuccessUrl()
                : returnUrl + "?result=success&session_id={CHECKOUT_SESSION_ID}";
        String cancelUrl = notBlank(stripeConfig.getCancelUrl())
                ? stripeConfig.getCancelUrl()
                : returnUrl + "?result=cancel";

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(user.getEmail())
                    .setClientReferenceId(user.getId().toString())
                    .setSuccessUrl(successUrl)
                    .setCancelUrl(cancelUrl)
                    .setLocale(SessionCreateParams.Locale.AUTO)
                    .setExpiresAt(Instant.now().plus(CHECKOUT_TTL_MINUTES, ChronoUnit.MINUTES).getEpochSecond())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(currency)
                                    .setUnitAmount(amountMinor)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName(productName)
                                                    .setDescription(productDescription)
                                                    .build())
                                    .build())
                            .build())
                    .putMetadata("type", type.name())
                    .putMetadata("referenceId", referenceId)
                    .putMetadata("userId", user.getId().toString())
                    .build();

            Session session = Session.create(params);

            paymentRepository.save(PaymentRecord.builder()
                    .paymentRef(session.getId())
                    .type(type)
                    .userId(user.getId())
                    .referenceId(referenceId)
                    .amount(amountMinor)
                    .currency(currency)
                    .build());

            return InitiatePaymentResponse.builder()
                    .payUrl(session.getUrl())
                    .paymentRef(session.getId())
                    .amount(CurrencyAmounts.toMajorUnits(amountMinor, currency))
                    .currency(currency)
                    .build();

        } catch (StripeException e) {
            log.error("Stripe error creating {} session: {}", type, e.getMessage(), e);
            throw new BadRequestException("Payment gateway error: " + e.getMessage());
        }
    }

    private PaymentRecord lockOwnRecord(String sessionId, UUID userId) {
        PaymentRecord record = paymentRepository.findForUpdate(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "ref", sessionId));
        if (!record.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Payment", "ref", sessionId);
        }
        return record;
    }

    private PaymentStatusResponse toStatus(PaymentRecord record) {
        return PaymentStatusResponse.builder()
                .paymentRef(record.getPaymentRef())
                .status(record.getStatus().name())
                .type(record.getType().name())
                .referenceId(record.getReferenceId())
                .amount(CurrencyAmounts.toMajorUnits(record.getAmount(), record.getCurrency()))
                .currency(record.getCurrency())
                .build();
    }

    private PaymentHistoryItem toHistoryItem(PaymentRecord record) {
        String description;
        if (record.getType() == PaymentType.BOOKING) {
            description = appointmentRepository.findById(UUID.fromString(record.getReferenceId()))
                    .map(a -> a.getSalon().getName() + " · " + a.getDate())
                    .orElse("Booking");
        } else {
            description = Optional.ofNullable(record.getReferenceId())
                    .map(p -> {
                        try {
                            return SubscriptionPlan.valueOf(p).getLabel() + " plan";
                        } catch (IllegalArgumentException e) {
                            return "Subscription";
                        }
                    })
                    .orElse("Subscription");
        }

        return PaymentHistoryItem.builder()
                .paymentRef(record.getPaymentRef())
                .type(record.getType().name())
                .status(record.getStatus().name())
                .amount(CurrencyAmounts.toMajorUnits(record.getAmount(), record.getCurrency()))
                .currency(record.getCurrency())
                .description(description)
                .referenceId(record.getReferenceId())
                .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().toString() : null)
                .build();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
