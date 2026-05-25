package com.loomea.auth.controller;

import com.loomea.auth.dto.*;
import com.loomea.auth.service.AuthService;
import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, login and profile")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user (returns pending — no JWT until email verified)")
    public ResponseEntity<ApiResponse<AuthResponse.PendingResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created. Please verify your email.", authService.register(request)));
    }

    @PostMapping("/request-otp")
    @Operation(summary = "Resend email verification OTP")
    public ResponseEntity<ApiResponse<String>> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        authService.requestOtp(request);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true).message("OTP sent to your email.").build());
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email with OTP — returns JWT on success")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Email verified successfully", authService.verifyEmail(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse result = authService.login(request);
        if (result == null) {
            AuthResponse.PendingResponse pending = AuthResponse.PendingResponse.builder()
                    .email(request.getEmail().toLowerCase())
                    .role("")
                    .message("Email not verified. A new verification code has been sent.")
                    .build();
            return ResponseEntity.ok(ApiResponse.success("Email not verified", pending));
        }
        return ResponseEntity.ok(ApiResponse.success("Login successful", result));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> getProfile(
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(authService.getProfile(userId)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile updated", authService.updateProfile(userId, request)));
    }

    @PutMapping("/me/avatar")
    @Operation(summary = "Upload avatar image (base64)", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> uploadAvatar(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody java.util.Map<String, String> body) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Avatar updated",
                authService.uploadAvatar(userId, body.get("imageData"))));
    }

    @GetMapping("/users/{userId}/avatar")
    @Operation(summary = "Serve user avatar image (public)")
    public ResponseEntity<byte[]> getAvatar(@PathVariable UUID userId) {
        byte[] data = authService.getAvatarData(userId);
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        return ResponseEntity.ok().headers(headers).body(data);
    }

    @PostMapping("/me/saved-salons/{salonId}")
    @Operation(summary = "Save a salon to favourites", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> saveSalon(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID salonId) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Salon saved", authService.saveSalon(userId, salonId)));
    }

    @DeleteMapping("/me/saved-salons/{salonId}")
    @Operation(summary = "Remove a salon from favourites", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> unsaveSalon(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID salonId) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Salon removed from favourites", authService.unsaveSalon(userId, salonId)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Schedule account for deletion (30-day grace period)", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "Account scheduled for deletion in 30 days.", authService.deleteAccount(userId)));
    }

    @PostMapping("/me/cancel-deletion")
    @Operation(summary = "Cancel a pending account deletion", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> cancelDeletion(
            @AuthenticationPrincipal UserDetails userDetails) {
        requireAuth(userDetails);
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                "Account deletion cancelled.", authService.cancelDeletion(userId)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void requireAuth(UserDetails userDetails) {
        if (userDetails == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
    }
}
