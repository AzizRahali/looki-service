package com.loomea.auth.controller;

import com.loomea.auth.dto.*;
import com.loomea.auth.service.PasswordResetService;
import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Recover account access via SMS OTP or email link")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // ── Step 1 ────────────────────────────────────────────────────────────────

    @PostMapping("/forgot-password")
    @Operation(
        summary = "Initiate password reset",
        description = """
            Step 1. Accepts a phone number (E.164) or email address.

            Always returns HTTP 200 — never reveals whether the account exists.

            **`method` in the response tells you what to do next:**
            | Value | Next step |
            |-------|-----------|
            | `sms` | User receives a 6-digit SMS code → call `/verify-recovery-otp` |
            | `email` | User receives an email link → call `/validate-reset-token` when link is opened |
            | `choice` | Account has both phone and email → resend with `preferredMethod` set |
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Recovery initiated",
            content = @Content(examples = @ExampleObject(name = "SMS sent", value = """
                {
                  "success": true,
                  "message": "Recovery initiated.",
                  "data": {
                    "method": "sms",
                    "sessionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "hint": "●●● ●●3"
                  }
                }
                """))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "rate_limited — too many OTP requests for this phone in the last hour"
        )
    })
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = passwordResetService.initiate(request);
        return ResponseEntity.ok(ApiResponse.success("Recovery initiated.", response));
    }

    // ── Step 2A: SMS path ─────────────────────────────────────────────────────

    @PostMapping("/verify-recovery-otp")
    @Operation(
        summary = "Verify SMS recovery OTP",
        description = """
            Step 2 (SMS path). Submit the 6-digit code the user received by SMS.

            - OTP expires in **5 minutes**.
            - Max **3 wrong attempts** — then locked; user must request a new code.
            - On success returns an opaque `resetToken` valid for **15 minutes**.
              Pass it to `/reset-password`.

            **Error patterns in the `message` field:**
            | Pattern | Meaning |
            |---------|---------|
            | `invalid_otp:2` | Wrong code, 2 attempts remaining |
            | `invalid_otp:1` | Wrong code, 1 attempt remaining |
            | `invalid_otp:0` | Wrong code, code now locked |
            | `too_many_attempts` | Already locked |
            | `code_expired` | OTP TTL exceeded |
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "OTP verified — reset token issued",
            content = @Content(examples = @ExampleObject(value = """
                {
                  "success": true,
                  "message": "OTP verified.",
                  "data": { "resetToken": "550e8400-e29b-41d4-a716-446655440000" }
                }
                """))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Wrong OTP, expired OTP, or code locked"
        )
    })
    public ResponseEntity<ApiResponse<Map<String, String>>> verifyRecoveryOtp(
            @Valid @RequestBody VerifyRecoveryOtpRequest request) {
        String resetToken = passwordResetService.verifyOtp(request);
        return ResponseEntity.ok(ApiResponse.success("OTP verified.", Map.of("resetToken", resetToken)));
    }

    // ── Step 2B: Email path ───────────────────────────────────────────────────

    @GetMapping("/validate-reset-token")
    @Operation(
        summary = "Validate email reset token",
        description = """
            Step 2 (email path). Called when the mobile app opens via the deep link
            `loomea://reset?token=...`.

            Returns `{ "valid": true }` if the token is unused and within the 15-minute TTL.
            The Flutter app then shows the new-password screen and passes this same token
            to `/reset-password`.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Token validity status",
            content = @Content(examples = @ExampleObject(value = """
                { "success": true, "data": { "valid": true } }
                """))
        )
    })
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> validateResetToken(
            @Parameter(
                description = "Raw UUID token from the email link `?token=` query parameter",
                example = "550e8400-e29b-41d4-a716-446655440000",
                required = true
            )
            @RequestParam String token) {
        boolean valid = passwordResetService.validateEmailToken(token);
        return ResponseEntity.ok(ApiResponse.success(Map.of("valid", valid)));
    }

    // ── Step 3 ────────────────────────────────────────────────────────────────

    @PostMapping("/reset-password")
    @Operation(
        summary = "Set new password",
        description = """
            Final step (both paths). Provide the `resetToken` from step 2 and the
            new password (min 8 characters).

            On success:
            - Password is updated (bcrypt-hashed).
            - Reset token is invalidated (single-use).
            - A fresh JWT `accessToken` is returned — client can auto-login.

            **Error codes in `message`:**
            | Code | Meaning |
            |------|---------|
            | `same_password` | New password is identical to the current one |
            | `token_expired` | Token TTL exceeded or already consumed |
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Password updated — JWT access token returned",
            content = @Content(examples = @ExampleObject(value = """
                {
                  "success": true,
                  "message": "Password reset successfully.",
                  "data": {
                    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiI...",
                    "user": {
                      "id": "550e8400-e29b-41d4-a716-446655440000",
                      "email": "user@example.com",
                      "role": "CLIENT"
                    }
                  }
                }
                """))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid/expired token or same-password rejection"
        )
    })
    public ResponseEntity<ApiResponse<AuthResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        AuthResponse response = passwordResetService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully.", response));
    }
}
