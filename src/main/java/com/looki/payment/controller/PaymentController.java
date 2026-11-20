package com.looki.payment.controller;

import com.looki.common.dto.ApiResponse;
import com.looki.common.exception.BadRequestException;
import com.looki.payment.dto.InitiatePaymentRequest;
import com.looki.payment.dto.InitiatePaymentResponse;
import com.looki.payment.dto.PaymentHistoryItem;
import com.looki.payment.dto.PaymentStatusResponse;
import com.looki.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
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

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        InitiatePaymentResponse response = paymentService.initiate(request, extractUserId(userDetails), baseUrl);
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

    /**
     * POST /api/payments/{sessionId}/cancel
     * The client left Checkout without paying. Expires the session and frees
     * the booking's slot. Returns the final status (COMPLETED if it was paid
     * in the meantime).
     */
    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> cancel(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        PaymentStatusResponse status = paymentService.cancel(sessionId, extractUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    /**
     * GET /api/payments/history
     * The current user's completed, refunded and failed payments, newest first.
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<PaymentHistoryItem>>> history(
            @AuthenticationPrincipal UserDetails userDetails) {

        return ResponseEntity.ok(ApiResponse.success(paymentService.getHistory(extractUserId(userDetails))));
    }

    /**
     * GET /api/payments/return?result=success|cancel
     * Public. Default Stripe success/cancel URL. The in-app WebView intercepts
     * it before it loads; when Checkout was opened in a browser (web, desktop)
     * the user lands here and is told to go back to the app, which is polling.
     */
    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> checkoutReturn(
            @RequestParam(defaultValue = "success") String result,
            @RequestParam(name = "session_id", required = false) String sessionId) {

        boolean success = "success".equals(result);
        if (success && sessionId != null && sessionId.startsWith("cs_")) {
            try {
                paymentService.syncSession(sessionId);
            } catch (Exception e) {
                log.warn("Could not sync session {} on return: {}", sessionId, e.getMessage());
            }
        }

        String title = success ? "Payment received" : "Payment cancelled";
        String message = success
                ? "Thanks! You can close this tab and go back to looki."
                : "No money was taken. You can close this tab and go back to looki.";
        String html = """
                <!doctype html>
                <html><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s · looki</title>
                <style>
                  body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                       font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#faf7f2;color:#1f2937}
                  main{text-align:center;padding:32px;max-width:360px}
                  h1{font-size:22px;margin:0 0 8px}
                  p{color:#6b7280;line-height:1.5;margin:0}
                </style></head>
                <body><main><h1>%s</h1><p>%s</p></main></body></html>
                """.formatted(title, title, message);
        return ResponseEntity.ok(html);
    }

    private UUID extractUserId(UserDetails userDetails) {
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid user principal: " + userDetails.getUsername());
        }
    }
}
