package com.loomea.subscription.dto;

import com.loomea.subscription.entity.SubscriptionPlan;
import lombok.Data;

@Data
public class SubscriptionRequest {

    /** MONTHLY or YEARLY */
    private SubscriptionPlan plan;

    /** Mock card number (only last 4 digits are stored). */
    private String cardNumber;

    /** MM/YY */
    private String cardExpiry;

    /** 3-digit CVV (not stored). */
    private String cardCvv;

    /** Cardholder name. */
    private String cardholderName;
}
