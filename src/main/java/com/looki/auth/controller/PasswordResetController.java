package com.looki.auth.controller;

import com.looki.auth.dto.*;
import com.looki.auth.service.PasswordResetService;
import com.looki.common.dto.ApiResponse;
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
@Tag(name = "Password Reset", description = "Recover account access via email link")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    // -- Step 1 ----------------------------------------------------------------

    @PostMapping("/forgot-password")
    @Operation(
        summary = "Initiate password reset",
        description = """
            Step 1. Accepts an email address.

            Always returns HTTP 200 -- never reveals whether the account exists.

            A reset link is sent to the email. The client should call
            `/validate-reset-token` when the deep link opens the app.
            """
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Recovery initiated",
            content = @Content(examples = @ExampleObject(value = """
                {
                  "success": true,
                  "message": "Recovery initiated.",
                  "data": {
                    "hint": "r*****@gmail.com"
                  }
                }
                """))
        )
    })
    public ResponseEntity<ApiResponse<ForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = passwordResetService.initiate(request);
        return ResponseEntity.ok(ApiResponse.success("Recovery initiated.", response));
    }

    // -- Step 2: Validate email token ------------------------------------------

    @GetMapping("/validate-reset-token")
    @Operation(
        summary = "Validate email reset token",
        description = """
            Step 2. Called when the mobile app opens via the deep link
            `looki://reset?token=...`.

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

    // -- Step 3 ----------------------------------------------------------------

    @PostMapping("/reset-password")
    @Operation(
        summary = "Set new password",
        description = """
            Final step. Provide the `resetToken` from the email link and the
            new password (min 8 characters).

            On success:
            - Password is updated (bcrypt-hashed).
            - Reset token is invalidated (single-use).
            - A fresh JWT `accessToken` is returned -- client can auto-login.

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
            description = "Password updated -- JWT access token returned",
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
