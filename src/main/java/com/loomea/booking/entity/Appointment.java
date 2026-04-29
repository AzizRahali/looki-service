package com.loomea.booking.entity;

import com.loomea.auth.entity.User;
import com.loomea.owner.entity.StaffMember;
import com.loomea.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "appointments", indexes = {
        @Index(name = "idx_appt_salon_date_status", columnList = "salon_id, date, status"),
        @Index(name = "idx_appt_user_salon_date", columnList = "user_id, salon_id, date, status"),
        @Index(name = "idx_appt_user_date_status", columnList = "user_id, date, status"),
        @Index(name = "idx_appt_status_created", columnList = "status, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.PAY_AT_SALON;

    private String notes;
    private double totalPrice;

    @Version
    @Builder.Default
    private Long version = 0L;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<AppointmentService> services = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    /**
     * Set to {@code true} when the booking belongs to a user whose score is below
     * {@link com.loomea.common.reliability.ReliabilityService#REQUIRE_CONFIRMATION_BELOW}.
     * The user must confirm attendance via {@code PUT /api/bookings/{id}/confirm-attendance}
     * at least 3 hours before the appointment start.
     */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    @Builder.Default
    private boolean requiresClientConfirmation = false;

    /**
     * Timestamp when the client confirmed they will attend.
     * {@code null} means not yet confirmed.
     */
    private Instant clientConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_member_id")
    private StaffMember staffMember;

    public void addService(AppointmentService service) {
        services.add(service);
        service.setAppointment(this);
    }
}
