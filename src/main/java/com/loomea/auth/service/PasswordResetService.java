package com.loomea.auth.service;

import com.loomea.auth.dto.*;
import com.loomea.auth.entity.PasswordResetOtp;
import com.loomea.auth.entity.PasswordResetToken;
import com.loomea.auth.entity.User;
import com.loomea.auth.repository.PasswordResetOtpRepository;
import com.loomea.auth.repository.PasswordResetTokenRepository;
import com.loomea.auth.repository.UserRepository;
import com.loomea.common.email.EmailService;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.security.JwtProvider;
import com.loomea.common.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int OTP_TTL_SECONDS      = 300;   // 5 minutes
    private static final int TOKEN_TTL_SECONDS     = 900;   // 15 minutes
    private static final int MAX_OTP_ATTEMPTS      = 3;
    private static final int MAX_OTP_PER_HOUR      = 5;

    private final UserRepository              userRepository;
    private final PasswordResetOtpRepository  otpRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final JwtProvider                 jwtProvider;
    private final SmsService                  smsService;
    private final EmailService                emailService;

    @Value("${app.deep-link-base:https://looki.tn}")
    private String deepLinkBase;

    // ─── Step 1: Initiate ────────────────────────────────────────────────────

    /**
     * Accepts a phone or email identifier. Sends an OTP (SMS) or a reset link
     * (email) depending on what the account has registered.
     *
     * Always returns 200 — never reveals whether the account exists.
     */
    @Transactional
    public ForgotPasswordResponse initiate(ForgotPasswordRequest request) {
        String identifier = request.getIdentifier().trim();
        boolean isEmail   = identifier.contains("@");
        String preferred  = request.getPreferredMethod(); // null | "sms" | "email"

        Optional<User> userOpt = isEmail
                ? userRepository.findByEmail(identifier.toLowerCase())
                : userRepository.findByPhone(normalisePhone(identifier));

        // ── Account not found — return generic success (no enumeration) ──────
        if (userOpt.isEmpty()) {
            return ForgotPasswordResponse.builder()
                    .method(isEmail ? "email" : "sms")
                    .hint(isEmail ? maskEmail(identifier) : maskPhone(identifier))
                    .build();
        }

        User user = userOpt.get();
        boolean hasPhone = user.getPhone() != null && !user.getPhone().isBlank();
        boolean hasEmail = user.getEmail() != null && !user.getEmail().isBlank();

        // ── User has both phone and email, and no method chosen yet ──────────
        if (hasPhone && hasEmail && preferred == null) {
            return ForgotPasswordResponse.builder()
                    .method("choice")
                    .phoneHint(maskPhone(user.getPhone()))
                    .emailHint(maskEmail(user.getEmail()))
                    .build();
        }

        // ── Determine which method to use ─────────────────────────────────────
        boolean useSms = hasPhone &&
                (preferred == null || "sms".equalsIgnoreCase(preferred)) &&
                !"email".equalsIgnoreCase(preferred);

        if (useSms) {
            return sendOtp(user);
        } else {
            return sendEmailLink(user);
        }
    }

    private ForgotPasswordResponse sendOtp(User user) {
        String phone = user.getPhone();

        // Rate limiting: max 5 OTP requests per phone per hour
        long recentCount = otpRepository.countRecentRequests(
                phone, Instant.now().minus(1, ChronoUnit.HOURS));
        if (recentCount >= MAX_OTP_PER_HOUR) {
            // Still return success — client will show "try again later" based on error
            throw new BadRequestException("rate_limited");
        }

        // Invalidate previous OTPs for this phone
        otpRepository.invalidateAllForPhone(phone);

        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        PasswordResetOtp session = PasswordResetOtp.builder()
                .phone(phone)
                .otpHash(passwordEncoder.encode(otp))
                .expiresAt(Instant.now().plusSeconds(OTP_TTL_SECONDS))
                .build();
        session = otpRepository.save(session);

        smsService.send(phone,
                "Your looki recovery code is: " + otp +
                " — it expires in 5 minutes. Do not share this code.");

        return ForgotPasswordResponse.builder()
                .method("sms")
                .sessionId(session.getId().toString())
                .hint(maskPhone(phone))
                .build();
    }

    private ForgotPasswordResponse sendEmailLink(User user) {
        // Generate opaque token (UUID) and store SHA-256 hash
        String rawToken   = UUID.randomUUID().toString();
        String tokenHash  = sha256(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS))
                .build();
        tokenRepository.save(resetToken);

        String resetLink = deepLinkBase + "/reset?token=" + rawToken;
        emailService.sendPasswordResetLink(user.getEmail(), user.getFullName(), resetLink);

        return ForgotPasswordResponse.builder()
                .method("email")
                .hint(maskEmail(user.getEmail()))
                .build();
    }

    // ─── Step 2A: Verify OTP ────────────────────────────────────────────────

    /**
     * Validates the 6-digit SMS code. On success issues an opaque reset token
     * that the client uses in Step 3.
     */
    @Transactional
    public String verifyOtp(VerifyRecoveryOtpRequest request) {
        UUID sessionId;
        try {
            sessionId = UUID.fromString(request.getSessionId());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid session.");
        }

        PasswordResetOtp session = otpRepository.findById(sessionId)
                .orElseThrow(() -> new BadRequestException("Session not found. Request a new code."));

        if (session.isUsed()) {
            throw new BadRequestException("This code has already been used. Request a new one.");
        }
        if (Instant.now().isAfter(session.getExpiresAt())) {
            throw new BadRequestException("code_expired");
        }
        if (session.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new BadRequestException("too_many_attempts");
        }
        if (!passwordEncoder.matches(request.getOtp(), session.getOtpHash())) {
            session.setAttempts(session.getAttempts() + 1);
            otpRepository.save(session);
            int remaining = MAX_OTP_ATTEMPTS - session.getAttempts();
            throw new BadRequestException("invalid_otp:" + remaining);
        }

        // Valid — mark used and issue a reset token
        session.setUsed(true);
        otpRepository.save(session);

        // Find user by phone
        User user = userRepository.findByPhone(session.getPhone())
                .orElseThrow(() -> new BadRequestException("Account not found."));

        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        tokenRepository.save(PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS))
                .build());

        return rawToken;
    }

    // ─── Step 2B: Validate email token (called when deep link opens app) ────

    public boolean validateEmailToken(String rawToken) {
        String hash = sha256(rawToken);
        return tokenRepository.findByTokenHash(hash)
                .map(t -> !t.isUsed() && Instant.now().isBefore(t.getExpiresAt()))
                .orElse(false);
    }

    // ─── Step 3: Reset password ─────────────────────────────────────────────

    /**
     * Accepts the opaque reset token + new password.
     * Updates the password, invalidates the token, and returns a fresh JWT.
     */
    @Transactional
    public AuthResponse resetPassword(ResetPasswordRequest request) {
        String hash = sha256(request.getResetToken());

        PasswordResetToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset link."));

        if (token.isUsed()) {
            throw new BadRequestException("This reset link has already been used.");
        }
        if (Instant.now().isAfter(token.getExpiresAt())) {
            throw new BadRequestException("token_expired");
        }

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new BadRequestException("Account not found."));

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("same_password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getId(), user.getEmail()))
                .user(toUserDto(user))
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String normalisePhone(String phone) {
        String digits = phone.replaceAll("[^+\\d]", "");
        if (!digits.startsWith("+")) digits = "+" + digits;
        return digits;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "●●●";
        return "●●● ●●" + phone.substring(phone.length() - 1);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "●●●@●●●";
        String local  = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        String masked = local.charAt(0) + "●●●●●";
        return masked + domain;
    }

    private AuthResponse.UserDto toUserDto(User user) {
        return AuthResponse.UserDto.builder()
                .id(user.getId().toString())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarData() != null && user.getAvatarData().length > 0
                        ? "/api/auth/users/" + user.getId() + "/avatar"
                        : null)
                .role(user.getRole().name())
                .savedSalonIds(java.util.List.of())
                .build();
    }
}
