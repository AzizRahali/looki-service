package com.loomea.staff.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StaffProfileDto {
    private String staffId;
    private String name;
    private String role;
    private String avatarUrl;
    private String salonId;
    private String salonName;
    private String salonAddress;
}
