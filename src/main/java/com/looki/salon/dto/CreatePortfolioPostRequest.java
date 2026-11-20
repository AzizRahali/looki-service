package com.looki.salon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePortfolioPostRequest {
    @NotBlank
    private String caption;

    @NotBlank
    private String imageData; // base64-encoded image bytes
}
