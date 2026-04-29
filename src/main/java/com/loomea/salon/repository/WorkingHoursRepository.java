package com.loomea.salon.repository;

import com.loomea.salon.entity.WorkingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface WorkingHoursRepository extends JpaRepository<WorkingHours, UUID> {

    List<WorkingHours> findBySalonIdOrderByDayOfWeek(UUID salonId);

    java.util.Optional<WorkingHours> findBySalonIdAndDayOfWeek(UUID salonId, int dayOfWeek);

    @Modifying
    @Query("DELETE FROM WorkingHours w WHERE w.salon.id = :salonId")
    void deleteBySalonId(UUID salonId);
}
