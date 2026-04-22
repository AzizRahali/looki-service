package com.loomea.subscription.controller;

import com.loomea.common.dto.ApiResponse;
import com.loomea.subscription.dto.SubscriptionDto;
import com.loomea.subscription.dto.SubscriptionRequest;
import com.loomea.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Subscribe (or renew) — SALON_OWNER only.
     * Accepts plan + mock card details, returns the new subscription.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SubscriptionDto>> subscribe(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody SubscriptionRequest request) {

        UUID userId = UUID.fromString(user.getUsername());
        SubscriptionDto dto = subscriptionService.subscribe(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Subscription activated", dto));
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
