package com.loomea.auth.repository;

import com.loomea.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);

    /** Returns all users who could still benefit from passive score recovery. */
    @Query("SELECT u FROM User u WHERE u.reliabilityScore < :maxScore")
    List<User> findUsersWithScoreBelowMax(@Param("maxScore") int maxScore);

    /** Returns IDs of users whose 30-day deletion grace period has expired. */
    @Query("SELECT u.id FROM User u WHERE u.scheduledDeletionAt IS NOT NULL AND u.scheduledDeletionAt <= :now")
    List<UUID> findUsersDueForDeletion(@Param("now") Instant now);
}
