package com.loomea.booking.repository;

import com.loomea.booking.entity.WaitlistEntry;
import com.loomea.booking.entity.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, UUID> {

    List<WaitlistEntry> findByUserIdAndStatus(UUID userId, WaitlistStatus status);

    List<WaitlistEntry> findBySalonIdAndDateAndStatusOrderByCreatedAtAsc(
            UUID salonId, LocalDate date, WaitlistStatus status);

    Optional<WaitlistEntry> findByUserIdAndSalonIdAndDate(
            UUID userId, UUID salonId, LocalDate date);
}
