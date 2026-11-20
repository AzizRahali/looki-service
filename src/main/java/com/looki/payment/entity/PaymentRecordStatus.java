package com.looki.payment.entity;

public enum PaymentRecordStatus {
    PENDING,
    COMPLETED,
    FAILED,
    /** Checkout abandoned or expired before the client paid. */
    CANCELLED,
    /** Paid, then refunded (booking cancelled/rejected after payment). */
    REFUNDED
}
