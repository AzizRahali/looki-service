package com.looki.subscription.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionDto {

    private String id;
    private String plan;       // MONTHLY | YEARLY
    private String planLabel;  // "Monthly" | "Yearly"
    private String priceLabel; // "$29.99 / month"
    private double price;
    private String status;     // ACTIVE | EXPIRED | CANCELLED
    private String startDate;
    private String endDate;
    private String paymentReference;
    private String cardLast4;
}
