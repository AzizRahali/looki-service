package com.loomea.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Describes what the backend initiated after a forgot-password request")
public class ForgotPasswordResponse {

    @Schema(
        description = "Masked email hint shown to the user, e.g. \"r*****@gmail.com\".",
        example = "r*****@gmail.com"
    )
    private String hint;
}
