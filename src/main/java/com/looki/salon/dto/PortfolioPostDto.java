package com.looki.salon.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PortfolioPostDto {
    private String id;
    private String salonId;
    private String caption;
    private String imageUrl;
    private String createdAt;
}
