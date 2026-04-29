package com.loomea.subscription.dto;

import com.loomea.subscription.entity.SubscriptionPlan;
import lombok.Data;

@Data
public class SubscriptionRequest {

    /** MONTHLY or YEARLY */
    private SubscriptionPlan plan;
}
