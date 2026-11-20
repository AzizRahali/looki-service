package com.looki.salon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "salon_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalonService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String description;
    private String category;
    private double price;
    private int durationMinutes;
    private String icon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;
}
