package com.loomea.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Describes what the backend initiated after a forgot-password request")
public class ForgotPasswordResponse {

    @Schema(
        description = "Delivery channel used. " +
                      "\"sms\" → call /verify-recovery-otp next. " +
                      "\"email\" → user opens email. " +
                      "\"choice\" → account has both phone and email; resend with preferredMethod.",
        example = "sms",
        allowableValues = {"sms", "email", "choice"}
    )
    private String method;

    @Schema(
        description = "Opaque session ID required for /verify-recovery-otp. " +
                      "Present only when method is \"sms\".",
        example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    )
    private String sessionId;

    @Schema(
        description = "Masked destination hint shown to the user. " +
                      "Phone example: \"●●● ●●3\". Email example: \"r●●●@gmail.com\". " +
                      "Absent when method is \"choice\".",
        example = "●●● ●●3"
    )
    private String hint;

    @Schema(
        description = "Masked phone number. Present only when method is \"choice\".",
        example = "+216 ●●● ●●3"
    )
    private String phoneHint;

    @Schema(
        description = "Masked email address. Present only when method is \"choice\".",
        example = "r●●●@gmail.com"
    )
    private String emailHint;
}
