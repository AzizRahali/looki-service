package com.loomea.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    private String name;

    private String description;
    private String category;

    @Positive(message = "Price must be positive")
    private double price;

    @Positive(message = "Duration must be positive")
    private int durationMinutes;

    private String icon;
}
