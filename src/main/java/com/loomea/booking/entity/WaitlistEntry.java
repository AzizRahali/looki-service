package com.loomea.booking.entity;

import com.loomea.auth.entity.User;
import com.loomea.salon.entity.Salon;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "waitlist_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "salon_id", "date"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "salon_id")
    private Salon salon;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @CreationTimestamp
    private Instant createdAt;
}
