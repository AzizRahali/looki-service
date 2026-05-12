package com.loomea.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Intermediate opaque token issued after OTP is verified (SMS path) or
 * embedded in the email reset link (email path).
 * The client presents this token to POST /reset-password.
 * Only the SHA-256 hash is stored; the plaintext UUID is sent to the client.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_prt_token_hash", columnList = "tokenHash", unique = true),
        @Index(name = "idx_prt_user_id",   columnList = "userId"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    /** SHA-256 hex digest of the plaintext token sent to the client. */
    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** 15-minute window. */
    @Column(nullable = false)
    private Instant expiresAt;

    /** Consumed on successful password reset. Single-use. */
    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;
}
