package com.looki.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KonnectPaymentDetail {

    private PaymentWrapper payment;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentWrapper {
        private String id;
        /** "completed" or "pending" */
        private String status;
        private Long amountDue;
        private Long reachedAmount;
        private Long amount;
        private String token;
        private String orderId;
        private String type;
    }
}
