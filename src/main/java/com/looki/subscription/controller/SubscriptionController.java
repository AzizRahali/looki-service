package com.looki.subscription.controller;

import com.looki.common.dto.ApiResponse;
import com.looki.subscription.dto.SubscriptionDto;
import com.looki.subscription.dto.SubscriptionPlanDto;
import com.looki.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Subscriptions are bought through Stripe: POST /api/payments/initiate with
 * type SUBSCRIPTION. The subscription is activated once the payment completes.
 */
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Available plans and prices.
     */
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.getPlans()));
    }

    /**
     * Get current owner's subscription status.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getMySubscription(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return subscriptionService.getMySubscription(userId)
                .map(dto -> ResponseEntity.ok(ApiResponse.success(null, dto)))
                .orElse(ResponseEntity.ok(ApiResponse.success("No active subscription", null)));
    }
}
