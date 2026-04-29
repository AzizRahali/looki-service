package com.loomea.salon.repository;

import com.loomea.salon.entity.Salon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalonRepository extends JpaRepository<Salon, UUID> {

    Optional<Salon> findByNameIgnoreCase(String name);

    @Query("SELECT s FROM Salon s WHERE " +
            "LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.address) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(s.description) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Salon> search(@Param("q") String query);

    @Query("SELECT DISTINCT s FROM Salon s JOIN s.services sv " +
            "WHERE LOWER(sv.category) = LOWER(:category)")
    List<Salon> findByServiceCategory(@Param("category") String category);

    @Query("SELECT s FROM Salon s WHERE " +
            "s.latitude BETWEEN :minLat AND :maxLat AND " +
            "s.longitude BETWEEN :minLng AND :maxLng")
    List<Salon> findNearby(
            @Param("minLat") double minLat, @Param("maxLat") double maxLat,
            @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    List<Salon> findByOwnerId(UUID ownerId);

    boolean existsByOwnerId(UUID ownerId);

    Optional<Salon> findByIdAndOwnerId(UUID id, UUID ownerId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Salon s SET s.owner = null WHERE s.owner.id = :ownerId")
    void clearOwnerById(@Param("ownerId") UUID ownerId);
}
