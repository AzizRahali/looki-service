package com.loomea.owner.dto;

import lombok.Data;

@Data
public class UpdateStaffRequest {
    private String name;
    private String role;
    private String avatarData; // base64, optional
}
