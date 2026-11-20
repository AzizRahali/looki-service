package com.looki.salon.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private double price;
    private int durationMinutes;
    private String icon;
}
