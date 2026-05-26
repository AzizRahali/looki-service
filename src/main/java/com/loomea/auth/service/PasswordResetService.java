package com.loomea.auth.service;

import com.loomea.auth.dto.*;
import com.loomea.auth.entity.PasswordResetToken;
import com.loomea.auth.entity.User;
import com.loomea.auth.repository.PasswordResetTokenRepository;
import com.loomea.auth.repository.UserRepository;
import com.loomea.common.email.EmailService;
import com.loomea.common.exception.BadRequestException;
import com.loomea.common.security.JwtProvider;
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
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_TTL_SECONDS = 900; // 15 minutes

    private final UserRepository              userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder             passwordEncoder;
    private final JwtProvider                 jwtProvider;
    private final EmailService                emailService;

    @Value("${app.deep-link-base:https://looki.tn}")
    private String deepLinkBase;

    // --- Step 1: Initiate (email only) ----------------------------------------

    @Transactional
    public ForgotPasswordResponse initiate(ForgotPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        Optional<User> userOpt = userRepository.findByEmail(email);

        // Account not found -- return generic success (no enumeration)
        if (userOpt.isEmpty()) {
            return ForgotPasswordResponse.builder()
                    .hint(maskEmail(email))
                    .build();
        }

        User user = userOpt.get();
        return sendEmailLink(user);
    }

    private ForgotPasswordResponse sendEmailLink(User user) {
        String rawToken  = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(TOKEN_TTL_SECONDS))
                .build();
        tokenRepository.save(resetToken);

        String resetLink = deepLinkBase + "/reset?token=" + rawToken;
        emailService.sendPasswordResetLink(user.getEmail(), user.getFullName(), resetLink);

        return ForgotPasswordResponse.builder()
                .hint(maskEmail(user.getEmail()))
                .build();
    }

    // --- Step 2: Validate email token -----------------------------------------

    public boolean validateEmailToken(String rawToken) {
        String hash = sha256(rawToken);
        return tokenRepository.findByTokenHash(hash)
                .map(t -> !t.isUsed() && Instant.now().isBefore(t.getExpiresAt()))
                .orElse(false);
    }

    // --- Step 3: Reset password -----------------------------------------------

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

    // --- Helpers --------------------------------------------------------------

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***@***";
        String local  = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@'));
        String masked = local.charAt(0) + "*****";
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
