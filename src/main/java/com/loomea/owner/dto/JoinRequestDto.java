package com.loomea.owner.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JoinRequestDto {
    private String id;
    private String salonId;
    private String userId;
    private String userName;
    private String userEmail;
    private String message;
    private String status;      // "PENDING" | "APPROVED" | "REJECTED"
    private String createdAt;
    private String resolvedAt;
}
