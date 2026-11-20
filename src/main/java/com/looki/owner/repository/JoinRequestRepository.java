package com.looki.owner.repository;

import com.looki.owner.entity.JoinRequest;
import com.looki.owner.entity.JoinRequest.JoinRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    List<JoinRequest> findBySalonIdAndStatusOrderByCreatedAtDesc(UUID salonId, JoinRequestStatus status);

    Optional<JoinRequest> findByIdAndSalonId(UUID id, UUID salonId);

    boolean existsBySalonIdAndUserIdAndStatus(UUID salonId, UUID userId, JoinRequestStatus status);
}
