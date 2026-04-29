package com.loomea.owner.repository;

import com.loomea.owner.entity.StaffMember;
import com.loomea.owner.entity.StaffStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffMemberRepository extends JpaRepository<StaffMember, UUID> {

    /** All non-removed staff for a salon (ACTIVE + SUSPENDED). */
    List<StaffMember> findBySalonIdAndStatusNot(UUID salonId, StaffStatus status);

    /** Only ACTIVE staff — used for booking assignment validation. */
    List<StaffMember> findBySalonIdAndStatus(UUID salonId, StaffStatus status);

    Optional<StaffMember> findByIdAndSalonId(UUID id, UUID salonId);

    Optional<StaffMember> findByInviteToken(String inviteToken);

    Optional<StaffMember> findByLinkedUserId(UUID linkedUserId);
}
