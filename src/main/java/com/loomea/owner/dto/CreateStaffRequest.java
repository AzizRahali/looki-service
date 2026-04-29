package com.loomea.owner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
    @NotBlank(message = "Staff name is required")
    private String name;
    private String role;
    private String avatarData; // base64, optional
}
