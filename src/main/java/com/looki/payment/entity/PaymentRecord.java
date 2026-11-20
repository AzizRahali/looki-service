package com.looki.payment.entity;

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

    /** Stripe Checkout Session id (cs_...). */
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

    /** Stripe PaymentIntent id (pi_...), stored once paid so it can be refunded. */
    private String paymentIntentId;

    /** Amount in the currency's smallest unit (millimes for TND, cents for USD). */
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
