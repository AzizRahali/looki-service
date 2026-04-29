package com.loomea.owner.entity;

import com.loomea.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "join_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"salon_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /** The user who is requesting to join. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Snapshot of the requester's name at request time. */
    @Column(name = "user_name")
    private String userName;

    /** Snapshot of the requester's email at request time. */
    @Column(name = "user_email")
    private String userEmail;

    /** Optional message from the applicant. */
    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @CreationTimestamp
    private Instant createdAt;

    /** Set when the owner approves or rejects. */
    private Instant resolvedAt;

    public enum JoinRequestStatus {
        PENDING, APPROVED, REJECTED
    }
}
