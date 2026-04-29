package com.loomea.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentStatusResponse {
    private String paymentRef;
    /** "PENDING" | "COMPLETED" | "FAILED" */
    private String status;
    private String type;
}
