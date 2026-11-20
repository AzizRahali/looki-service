package com.looki.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentStatusResponse {
    private String paymentRef;
    /** "PENDING" | "COMPLETED" | "FAILED" | "CANCELLED" | "REFUNDED" */
    private String status;
    private String type;
    /** Appointment id for BOOKING, plan name for SUBSCRIPTION. */
    private String referenceId;
    private double amount;
    private String currency;
}
