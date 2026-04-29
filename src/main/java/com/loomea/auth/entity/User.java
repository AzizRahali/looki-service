package com.loomea.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String phone;

    private String avatarUrl;

    @Lob
    @Column(name = "avatar_data")
    private byte[] avatarData;

    private String location;

    @Builder.Default
    private boolean phoneVerified = false;

    private String otpCode;

    private Instant otpExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.CLIENT;

    @CreationTimestamp
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "user_saved_salons", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "salon_id")
    @Builder.Default
    private List<UUID> savedSalonIds = new ArrayList<>();

    // ── Reliability score ─────────────────────────────────────────────────────

    /** Current reliability score. Bounded [0, 120], starts at 100. */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 100")
    @Builder.Default
    private int reliabilityScore = 100;

    /** Number of consecutively completed appointments (resets on no-show / late cancel). */
    @Column(nullable = false, columnDefinition = "INT DEFAULT 0")
    @Builder.Default
    private int consecutiveCompletions = 0;

    /** Timestamp of the last negative action (no-show or late cancellation). */
    private Instant lastNegativeActionAt;

    /** If non-null and in the future, the user cannot make new bookings. */
    private Instant bookingBlockedUntil;

    /** Timestamp when passive recovery was last applied. */
    private Instant lastPassiveRecoveryAt;

    // ── Soft-delete / grace period ────────────────────────────────────────────

    /**
     * If non-null, the account is scheduled for permanent deletion at this timestamp.
     * The user has a 30-day grace period to cancel. Null = active account.
     */
    private Instant scheduledDeletionAt;

}
