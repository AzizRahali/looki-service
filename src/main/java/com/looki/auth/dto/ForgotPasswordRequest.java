package com.looki.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Initiate a password reset via email link")
public class ForgotPasswordRequest {

    @NotBlank
    @Email
    @Schema(
        description = "Email address of the account to recover",
        example = "user@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String email;
}
