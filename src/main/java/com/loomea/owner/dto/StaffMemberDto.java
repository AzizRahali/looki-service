package com.loomea.owner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaffMemberDto {
    private String id;
    private String salonId;
    private String name;
    private String role;          // job title (e.g. "Hairdresser")
    private String staffRole;     // salon role: "ADMIN" | "STAFF"
    private String status;        // "ACTIVE" | "SUSPENDED" | "REMOVED"
    private String avatarUrl;
    private String inviteToken;
    private String inviteStatus;  // "none" | "pending" | "active"
}
