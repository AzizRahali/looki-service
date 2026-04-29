package com.loomea.owner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AssignStaffRoleRequest {
    /** "ADMIN" or "STAFF" */
    @NotBlank
    private String staffRole;
}
