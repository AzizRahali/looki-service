package com.loomea.owner.entity;

import com.loomea.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staff_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(nullable = false)
    private String name;

    /** Job title / specialisation (e.g. "Hairdresser"). Distinct from staffRole. */
    private String role;

    @Column(name = "avatar_data", columnDefinition = "bytea")
    private byte[] avatarData;

    /** Lifecycle status — replaces the old boolean `active` field. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'ACTIVE'")
    @Builder.Default
    private StaffStatus status = StaffStatus.ACTIVE;

    /** Role within the salon management hierarchy. */
    @Enumerated(EnumType.STRING)
    @Column(name = "staff_role", nullable = false, columnDefinition = "varchar(255) default 'STAFF'")
    @Builder.Default
    private StaffRole staffRole = StaffRole.STAFF;

    @Column(name = "invite_token", unique = true)
    private String inviteToken;

    @Column(name = "invite_token_expires_at")
    private LocalDateTime inviteTokenExpiresAt;

    /** UUID of the User account linked to this member after invite acceptance. */
    @Column(name = "linked_user_id")
    private UUID linkedUserId;

    // ── Convenience helpers ───────────────────────────────────────────────────

    public boolean isActive()    { return status == StaffStatus.ACTIVE; }
    public boolean isSuspended() { return status == StaffStatus.SUSPENDED; }
    public boolean isRemoved()   { return status == StaffStatus.REMOVED; }
}
