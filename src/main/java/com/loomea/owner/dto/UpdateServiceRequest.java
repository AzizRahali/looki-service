package com.loomea.owner.dto;

import lombok.Data;

@Data
public class UpdateServiceRequest {
    private String name;
    private String description;
    private String category;
    private Double price;
    private Integer durationMinutes;
    private String icon;
}
