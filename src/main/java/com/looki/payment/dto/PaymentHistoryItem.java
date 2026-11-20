package com.looki.payment.dto;

import lombok.Builder;
import lombok.Data;

/** One row of a user's payment history (completed, refunded or failed charges). */
@Data
@Builder
public class PaymentHistoryItem {
    private String paymentRef;
    /** "BOOKING" | "SUBSCRIPTION" */
    private String type;
    /** "COMPLETED" | "REFUNDED" | "FAILED" */
    private String status;
    private double amount;
    private String currency;
    /** e.g. "Luna Hair Studio · 2026-11-24" or "Monthly plan". */
    private String description;
    /** Appointment id for BOOKING, plan name for SUBSCRIPTION. */
    private String referenceId;
    private String createdAt;
}
