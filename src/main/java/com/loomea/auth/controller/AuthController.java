package com.loomea.auth.controller;

import com.loomea.auth.dto.*;
import com.loomea.auth.service.AuthService;
import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    @Operation(summary = "Register a new user (returns pending — no JWT until phone verified)")
    public ResponseEntity<ApiResponse<AuthResponse.PendingResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account created. Please verify your phone.", authService.register(request)));
    }

    @PostMapping("/request-otp")
    @Operation(summary = "Request phone verification OTP")
    public ResponseEntity<ApiResponse<String>> requestOtp(@Valid @RequestBody RequestOtpRequest request) {
        authService.requestOtp(request);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true).message("OTP sent to your phone.").build());
    }

    @PostMapping("/verify-phone")
    @Operation(summary = "Verify phone with OTP — returns JWT on success")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Phone verified successfully", authService.verifyPhone(request)));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
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
