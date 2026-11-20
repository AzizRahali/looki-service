package com.looki.payment.service;

import com.looki.booking.event.AppointmentCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Refunds card payments for cancelled/rejected appointments. Runs only after
 * the cancellation has committed, so a rolled-back cancel never refunds.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingRefundListener {

    private final PaymentService paymentService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        try {
            paymentService.refundBookingIfPaid(event.appointmentId());
        } catch (Exception e) {
            log.error("Refund check failed for appointment {}: {}", event.appointmentId(), e.getMessage(), e);
        }
    }
}
