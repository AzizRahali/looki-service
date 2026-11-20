package com.looki.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Set a new password using a valid reset token")
public class ResetPasswordRequest {

    @NotBlank
    @Schema(
        description = "Opaque reset token issued by /verify-recovery-otp (SMS path) " +
                      "or extracted from the email deep-link query parameter.",
        example = "eyJhbGciOiJIUzI1NiJ9.reset-token-example",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String resetToken;

    @NotBlank
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Schema(
        description = "New password. Minimum 8 characters. Must differ from the current password.",
        example = "MyNewP@ss2026",
        minLength = 8,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String newPassword;
}
