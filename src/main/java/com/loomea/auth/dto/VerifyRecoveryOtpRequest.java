package com.loomea.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "Verify the 6-digit SMS OTP to obtain a password reset token")
public class VerifyRecoveryOtpRequest {

    @NotBlank
    @Schema(
        description = "Session ID returned by /forgot-password when method was \"sms\"",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String sessionId;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    @Schema(
        description = "6-digit numeric code sent to the user's phone. Expires in 5 minutes.",
        example = "482931",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String otp;
}
