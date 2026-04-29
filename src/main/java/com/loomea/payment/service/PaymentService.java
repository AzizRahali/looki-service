package com.loomea.payment.service;

import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import com.loomea.booking.dto.BookingDto;
import com.loomea.booking.dto.CreateBookingRequest;
import com.loomea.booking.entity.Appointment;
import com.loomea.booking.entity.AppointmentStatus;
import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.booking.service.BookingService;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.payment.config.StripeConfig;
import com.loomea.payment.dto.InitiatePaymentRequest;
import com.loomea.payment.dto.InitiatePaymentResponse;
import com.loomea.payment.dto.PaymentStatusResponse;
import com.loomea.payment.entity.PaymentRecord;
import com.loomea.payment.entity.PaymentRecordStatus;
import com.loomea.payment.entity.PaymentType;
import com.loomea.payment.repository.PaymentRepository;
import com.loomea.subscription.entity.Subscription;
import com.loomea.subscription.entity.SubscriptionPlan;
import com.loomea.subscription.entity.SubscriptionStatus;
import com.loomea.subscription.repository.SubscriptionRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StripeConfig stripeConfig;
    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final AppointmentRepository appointmentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    // Stripe uses the smallest currency unit (cents for USD)
    private static final String CURRENCY = "usd";

    // ── Public API ─────────────────────────────────────────────────────────────

    @Transactional
    public InitiatePaymentResponse initiate(InitiatePaymentRequest req, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return switch (req.getType().toUpperCase()) {
            case "BOOKING"      -> initiateBookingPayment(req, user);
            case "SUBSCRIPTION" -> initiateSubscriptionPayment(req, user);
            default -> throw new BadRequestException("Unknown payment type: " + req.getType());
        };
    }

    /**
     * Called by the Stripe webhook (POST /api/payments/webhook).
     * Verifies the Stripe-Signature header, then fulfils the order if completed.
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

        if (!"checkout.session.completed".equals(event.getType())) return;

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (session == null) return;

        processSession(session.getId());
    }

    /**
     * Polled by Flutter every few seconds.
     * If still PENDING, actively asks Stripe for the latest state and fulfils inline —
     * so polling works even without a configured webhook secret (local dev, staging).
     */
    @Transactional
    public PaymentStatusResponse getStatus(String sessionId, UUID userId) {
        PaymentRecord record = paymentRepository.findByPaymentRefAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "ref", sessionId));

        if (record.getStatus() == PaymentRecordStatus.PENDING) {
            verifyAndFulfil(record);
        }

        return PaymentStatusResponse.builder()
                .paymentRef(record.getPaymentRef())
                .status(record.getStatus().name())
                .type(record.getType().name())
                .build();
    }

    // ── Shared fulfillment ─────────────────────────────────────────────────────

    private void processSession(String sessionId) {
        PaymentRecord record = paymentRepository.findByPaymentRef(sessionId).orElse(null);
        if (record == null) {
            log.warn("Webhook: no PaymentRecord for Stripe session {}", sessionId);
            return;
        }
        if (record.getStatus() != PaymentRecordStatus.PENDING) {
            log.info("Webhook ignored — session {} already {}", sessionId, record.getStatus());
            return;
        }
        verifyAndFulfil(record);
    }

    private void verifyAndFulfil(PaymentRecord record) {
        try {
            Session session = Session.retrieve(record.getPaymentRef());
            if (!"paid".equals(session.getPaymentStatus())) return;

            try {
                if (record.getType() == PaymentType.BOOKING) {
                    fulfillBooking(record);
                } else if (record.getType() == PaymentType.SUBSCRIPTION) {
                    fulfillSubscription(record);
                }
                record.setStatus(PaymentRecordStatus.COMPLETED);
            } catch (Exception e) {
                log.error("Error fulfilling payment {}: {}", record.getPaymentRef(), e.getMessage(), e);
                record.setStatus(PaymentRecordStatus.FAILED);
            }
            paymentRepository.save(record);

        } catch (StripeException e) {
            log.error("Stripe API error verifying session {}: {}", record.getPaymentRef(), e.getMessage());
        }
    }

    // ── BOOKING ────────────────────────────────────────────────────────────────

    private InitiatePaymentResponse initiateBookingPayment(InitiatePaymentRequest req, User user) {
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
        bookingReq.setStaffMemberId(req.getStaffMemberId());

        BookingDto booking = bookingService.create(user.getId(), bookingReq);
        String appointmentId = booking.getId();
        long amountCents = Math.round(booking.getTotalPrice() * 100);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(user.getEmail())
                    .setSuccessUrl(stripeConfig.getSuccessUrl())
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(CURRENCY)
                                    .setUnitAmount(amountCents)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Booking at " + booking.getSalonName())
                                                    .build())
                                    .build())
                            .build())
                    .putMetadata("type", "BOOKING")
                    .putMetadata("referenceId", appointmentId)
                    .putMetadata("userId", user.getId().toString())
                    .build();

            Session session = Session.create(params);

            paymentRepository.save(PaymentRecord.builder()
                    .paymentRef(session.getId())
                    .type(PaymentType.BOOKING)
                    .userId(user.getId())
                    .referenceId(appointmentId)
                    .amount(amountCents)
                    .currency(CURRENCY)
                    .build());

            return InitiatePaymentResponse.builder()
                    .payUrl(session.getUrl())
                    .paymentRef(session.getId())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe error creating booking session: {}", e.getMessage(), e);
            throw new BadRequestException("Payment gateway error: " + e.getMessage());
        }
    }

    private void fulfillBooking(PaymentRecord record) {
        UUID appointmentId = UUID.fromString(record.getReferenceId());
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", "id", appointmentId));

        if (appt.getStatus() == AppointmentStatus.PENDING) {
            appt.setStatus(AppointmentStatus.CONFIRMED);
            appointmentRepository.save(appt);
            log.info("Appointment {} confirmed via Stripe session {}", appointmentId, record.getPaymentRef());
        }
    }

    // ── SUBSCRIPTION ───────────────────────────────────────────────────────────

    private InitiatePaymentResponse initiateSubscriptionPayment(InitiatePaymentRequest req, User user) {
        if (req.getPlan() == null) {
            throw new BadRequestException("plan is required for SUBSCRIPTION");
        }

        SubscriptionPlan plan;
        try {
            plan = SubscriptionPlan.valueOf(req.getPlan().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown subscription plan: " + req.getPlan());
        }

        long amountCents = Math.round(plan.getPrice() * 100);

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setCustomerEmail(user.getEmail())
                    .setSuccessUrl(stripeConfig.getSuccessUrl())
                    .setCancelUrl(stripeConfig.getCancelUrl())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(CURRENCY)
                                    .setUnitAmount(amountCents)
                                    .setProductData(
                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                    .setName("Loomea " + plan.getLabel() + " subscription")
                                                    .build())
                                    .build())
                            .build())
                    .putMetadata("type", "SUBSCRIPTION")
                    .putMetadata("referenceId", plan.name())
                    .putMetadata("userId", user.getId().toString())
                    .build();

            Session session = Session.create(params);

            paymentRepository.save(PaymentRecord.builder()
                    .paymentRef(session.getId())
                    .type(PaymentType.SUBSCRIPTION)
                    .userId(user.getId())
                    .referenceId(plan.name())
                    .amount(amountCents)
                    .currency(CURRENCY)
                    .build());

            return InitiatePaymentResponse.builder()
                    .payUrl(session.getUrl())
                    .paymentRef(session.getId())
                    .build();

        } catch (StripeException e) {
            log.error("Stripe error creating subscription session: {}", e.getMessage(), e);
            throw new BadRequestException("Payment gateway error: " + e.getMessage());
        }
    }

    private void fulfillSubscription(PaymentRecord record) {
        UUID userId = record.getUserId();
        SubscriptionPlan plan = SubscriptionPlan.valueOf(record.getReferenceId());

        subscriptionRepository.findByUserIdAndStatus(userId, SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(SubscriptionStatus.CANCELLED);
                    subscriptionRepository.save(existing);
                });

        Subscription sub = Subscription.builder()
                .userId(userId)
                .plan(plan)
                .status(SubscriptionStatus.ACTIVE)
                .startDate(Instant.now())
                .endDate(Instant.now().plus(plan.getDurationDays(), ChronoUnit.DAYS))
                .paymentReference(record.getPaymentRef())
                .build();

        subscriptionRepository.save(sub);
        log.info("Subscription activated for user {} plan {} via Stripe session {}",
                userId, plan, record.getPaymentRef());
    }
}
