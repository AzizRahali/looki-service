package com.loomea.salon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdatePortfolioPostRequest {
    @NotBlank
    private String caption;

    private String imageData; // optional base64-encoded image bytes — keep existing if null/blank
}
