package com.loomea.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Initiate a password reset via SMS OTP or email link")
public class ForgotPasswordRequest {

    @NotBlank
    @Schema(
        description = "Phone number (E.164) or email address of the account to recover",
        example = "+21698123456",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String identifier;

    @Schema(
        description = "Force a specific delivery channel when the account has both phone and email. " +
                      "Omit on the first call; include only when the response method is \"choice\".",
        example = "sms",
        allowableValues = {"sms", "email"}
    )
    private String preferredMethod;
}
