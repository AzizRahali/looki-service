package com.looki.payment.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InitiatePaymentResponse {
    private String payUrl;
    private String paymentRef;
    /** What Stripe will actually charge, after currency conversion. */
    private double amount;
    private String currency;
}
