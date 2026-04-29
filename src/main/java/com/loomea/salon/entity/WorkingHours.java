package com.loomea.salon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "working_hours",
       uniqueConstraints = @UniqueConstraint(columnNames = {"salon_id", "day_of_week"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    /** 0 = Monday … 6 = Sunday */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    private LocalTime openTime;
    private LocalTime closeTime;

    @Column(nullable = false)
    private boolean closed;
}
