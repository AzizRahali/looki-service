package com.looki.booking.entity;

/**
 * Online-payment state of a PAY_WITH_CARD appointment.
 * Always {@code null} for PAY_AT_SALON bookings.
 */
public enum AppointmentPaymentStatus {
    /** Checkout created, client hasn't paid yet. */
    AWAITING_PAYMENT,
    PAID,
    REFUNDED
}
