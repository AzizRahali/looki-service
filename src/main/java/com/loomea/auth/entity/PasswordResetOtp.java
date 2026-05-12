package com.loomea.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Short-lived OTP record used during password recovery via SMS.
 * Stored separately from the User entity to avoid coupling with
 * the phone-verification OTP already on User.
 */
@Entity
@Table(name = "password_reset_otps", indexes = {
        @Index(name = "idx_prot_phone", columnList = "phone"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Normalised E.164 phone number (e.g. +21612345678). */
    @Column(nullable = false)
    private String phone;

    /** bcrypt hash of the 6-digit code. Never store plaintext. */
    @Column(nullable = false)
    private String otpHash;

    /** Number of failed verification attempts (max 3 before lockout). */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** 5-minute window. */
    @Column(nullable = false)
    private Instant expiresAt;

    /** Marked true after successful verification or when superseded by a resend. */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;
}
