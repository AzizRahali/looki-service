package com.looki.salon.repository;

import com.looki.salon.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    @Query("SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.salon WHERE r.salon.id = :salonId ORDER BY r.createdAt DESC")
    List<Review> findBySalonIdOrderByCreatedAtDesc(@Param("salonId") UUID salonId);
    @Query("SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.salon WHERE r.salon.id = :salonId AND r.user.id = :userId")
    Optional<Review> findBySalonIdAndUserId(@Param("salonId") UUID salonId, @Param("userId") UUID userId);

    @Query("SELECT r FROM Review r JOIN FETCH r.user JOIN FETCH r.salon WHERE r.id = :id")
    Optional<Review> findByIdWithRelations(@Param("id") UUID id);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.salon.id = :salonId")
    double averageRatingBySalon(@Param("salonId") UUID salonId);

    long countBySalonId(UUID salonId);

    @Modifying
    @Query("DELETE FROM Review r WHERE r.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
