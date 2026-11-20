package com.looki.booking.repository;

import com.looki.booking.entity.WaitlistEntry;
import com.looki.booking.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    @Query("SELECT w FROM WaitlistEntry w JOIN FETCH w.salon JOIN FETCH w.user WHERE w.user.id = :userId AND w.status = :status")
    List<WaitlistEntry> findByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") WaitlistStatus status);

    List<WaitlistEntry> findBySalonIdAndDateAndStatusOrderByCreatedAtAsc(
            UUID salonId, LocalDate date, WaitlistStatus status);

    Optional<WaitlistEntry> findByUserIdAndSalonIdAndDate(
            UUID userId, UUID salonId, LocalDate date);
}
