package com.looki.subscription.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionPlanDto {
    private String plan;        // MONTHLY | YEARLY
    private String label;       // "Monthly" | "Yearly"
    private double price;
    private int durationDays;
}
