package com.loomea.auth.service;

import com.loomea.auth.dto.*;
import com.loomea.auth.entity.Role;
import com.loomea.auth.entity.User;
import com.loomea.auth.repository.UserRepository;
import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.exception.ConflictException;
import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.common.reliability.ReliabilityTier;
import com.loomea.common.security.JwtProvider;
import com.loomea.common.sms.SmsService;
import com.loomea.salon.repository.ReviewRepository;
import com.loomea.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final AuthenticationManager authenticationManager;
    private final SmsService smsService;
    private final AppointmentRepository appointmentRepository;
    private final ReviewRepository reviewRepository;
    private final SalonRepository salonRepository;

    /** Step 1: create account (unverified). Returns pending response — no JWT yet. */
    @Transactional
    public AuthResponse.PendingResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().toLowerCase())) {
            throw new ConflictException("Email is already registered");
        }
        Role role = Role.CLIENT;
        if (request.getRole() != null && request.getRole().equalsIgnoreCase("SALON_OWNER")) {
            role = Role.SALON_OWNER;
        }
        userRepository.save(User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(role)
                .phoneVerified(false)
                .build());
        return AuthResponse.PendingResponse.builder()
                .email(request.getEmail().toLowerCase())
                .role(role.name())
                .message("Account created. Please verify your phone number.")
                .build();
    }

    /** Step 2: generate + log OTP, store hashed on user. */
    @Transactional
    public void requestOtp(RequestOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));
        if (user.isPhoneVerified()) {
            throw new BadRequestException("Phone already verified");
        }
        // Update phone number
        user.setPhone(request.getPhone());
        // Generate 4-digit OTP
        String otp = String.format("%04d", new Random().nextInt(10000));
        user.setOtpCode(passwordEncoder.encode(otp));
        user.setOtpExpiresAt(Instant.now().plusSeconds(600)); // 10 min
        userRepository.save(user);
        // Send via Twilio in production, console log in dev (auto-selected by Spring)
        smsService.send(request.getPhone(),
                "Your Loomea verification code is: " + otp + ". It expires in 10 minutes.");
    }

    /** Step 3: verify OTP → return JWT. */
    @Transactional
    public AuthResponse verifyPhone(VerifyPhoneRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));
        if (user.isPhoneVerified()) {
            // Already verified — just return a token
            return toAuthResponse(user);
        }
        if (user.getOtpCode() == null || user.getOtpExpiresAt() == null) {
            throw new BadRequestException("No OTP requested. Please request a code first.");
        }
        if (Instant.now().isAfter(user.getOtpExpiresAt())) {
            throw new BadRequestException("OTP has expired. Please request a new code.");
        }
        if (!passwordEncoder.matches(request.getOtp(), user.getOtpCode())) {
            throw new BadRequestException("Invalid verification code.");
        }
        user.setPhoneVerified(true);
        user.setOtpCode(null);
        user.setOtpExpiresAt(null);
        userRepository.save(user);
        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", request.getEmail()));
        if (!user.isPhoneVerified()) {
            throw new BadRequestException("Phone number not verified. Please complete phone verification.");
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return toUserDto(user);
    }

    @Transactional
    public AuthResponse.UserDto updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (request.getFullName() != null)  user.setFullName(request.getFullName());
        if (request.getPhone() != null)     user.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getLocation() != null)  user.setLocation(request.getLocation());
        return toUserDto(userRepository.save(user));
    }

    @Transactional
    public AuthResponse.UserDto saveSalon(UUID userId, UUID salonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (!user.getSavedSalonIds().contains(salonId)) {
            user.getSavedSalonIds().add(salonId);
        }
        return toUserDto(userRepository.save(user));
    }

    @Transactional
    public AuthResponse.UserDto unsaveSalon(UUID userId, UUID salonId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.getSavedSalonIds().remove(salonId);
        return toUserDto(userRepository.save(user));
    }

    /**
     * Schedules the account for deletion in 30 days (soft-delete).
     * The user can still authenticate and cancel during the grace period.
     * A nightly job ({@link com.loomea.auth.service.AccountDeletionService}) performs
     * the actual hard-delete when {@code scheduledDeletionAt} has passed.
     */
    @Transactional
    public AuthResponse.UserDto deleteAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        user.setScheduledDeletionAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return toUserDto(userRepository.save(user));
    }

    /**
     * Cancels a pending account deletion (clears the scheduled deletion timestamp).
     */
    @Transactional
    public AuthResponse.UserDto cancelDeletion(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getScheduledDeletionAt() == null) {
            throw new BadRequestException("No pending deletion to cancel.");
        }
        user.setScheduledDeletionAt(null);
        return toUserDto(userRepository.save(user));
    }

    /**
     * Hard-deletes a user and all their data. Called by the nightly job after the
     * 30-day grace period, or directly for admin use.
     */
    @Transactional
    public void hardDeleteAccount(UUID userId) {
        if (!userRepository.existsById(userId)) return;
        appointmentRepository.findByUserId(userId)
                .forEach(appointmentRepository::delete);
        reviewRepository.deleteByUserId(userId);
        salonRepository.clearOwnerById(userId);
        userRepository.deleteById(userId);
    }

    private AuthResponse toAuthResponse(User user) {
        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .user(toUserDto(user))
                .build();
    }

    private AuthResponse.UserDto toUserDto(User user) {
        List<String> savedIds = user.getSavedSalonIds().stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        return AuthResponse.UserDto.builder()
                .id(user.getId().toString())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .location(user.getLocation())
                .role(user.getRole().name())
                .savedSalonIds(savedIds)
                .reliabilityScore(user.getReliabilityScore())
                .reliabilityTier(ReliabilityTier.fromScore(user.getReliabilityScore()).name())
                .scheduledDeletionAt(user.getScheduledDeletionAt() != null
                        ? user.getScheduledDeletionAt().toString()
                        : null)
                .build();
    }
}
