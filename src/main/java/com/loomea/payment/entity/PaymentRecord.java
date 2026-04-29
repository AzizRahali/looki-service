package com.loomea.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_records", indexes = {
        @Index(name = "idx_pr_payment_ref", columnList = "paymentRef", unique = true),
        @Index(name = "idx_pr_user_id",     columnList = "userId"),
        @Index(name = "idx_pr_ref_id",      columnList = "referenceId"),
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Konnect-assigned payment reference (returned from /payments/init-payment). */
    @Column(unique = true, nullable = false)
    private String paymentRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    /** Authenticated user who initiated the payment. */
    @Column(nullable = false)
    private UUID userId;

    /**
     * Type-dependent reference:
     *   BOOKING      → appointment UUID string
     *   SUBSCRIPTION → "MONTHLY" or "YEARLY"
     */
    private String referenceId;

    /** Amount in millimes (TND×1000) or centimes (EUR/USD×100). */
    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PaymentRecordStatus status = PaymentRecordStatus.PENDING;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
