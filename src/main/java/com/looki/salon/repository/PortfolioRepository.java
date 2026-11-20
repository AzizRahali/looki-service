package com.looki.salon.repository;

import com.looki.salon.entity.PortfolioPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<PortfolioPost, UUID> {
    List<PortfolioPost> findBySalonIdOrderByCreatedAtDesc(UUID salonId);
    long countBySalonId(UUID salonId);
}
