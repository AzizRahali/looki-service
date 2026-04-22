package com.loomea.salon.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "portfolio_posts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PortfolioPost {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_id", nullable = false)
    private Salon salon;

    @Column(nullable = false, length = 200)
    private String caption;

    @Column(nullable = false)
    private String imageUrl; // stores the path "/api/portfolio/{id}/image"

    @Column(name = "image_data", columnDefinition = "bytea")
    private byte[] imageData;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
