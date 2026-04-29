package com.loomea.payment.controller;

import com.loomea.common.dto.ApiResponse;
import com.loomea.common.exception.BadRequestException;
import com.loomea.payment.dto.InitiatePaymentRequest;
import com.loomea.payment.dto.InitiatePaymentResponse;
import com.loomea.payment.dto.PaymentStatusResponse;
import com.loomea.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * POST /api/payments/initiate
     * Creates a Stripe Checkout Session and returns the hosted payment URL.
     * For BOOKING: also atomically creates the appointment as PENDING.
     */
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<InitiatePaymentResponse>> initiate(
            @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        InitiatePaymentResponse response = paymentService.initiate(request, extractUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * POST /api/payments/webhook
     * Public. Stripe calls this after every payment status change.
     * Signature is verified inside the service via Stripe-Signature header.
     * Always returns 200 — Stripe retries on non-2xx responses.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        try {
            paymentService.handleWebhook(payload, sigHeader);
        } catch (Exception e) {
            log.error("Webhook processing error: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }

    /**
     * GET /api/payments/status/{sessionId}
     * Polled by Flutter every few seconds. If still PENDING, verifies with
     * Stripe directly and fulfils the order inline — works without a webhook.
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        PaymentStatusResponse status = paymentService.getStatus(sessionId, extractUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    private UUID extractUserId(UserDetails userDetails) {
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid user principal: " + userDetails.getUsername());
        }
    }
}
