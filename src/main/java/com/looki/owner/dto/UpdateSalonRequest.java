package com.looki.owner.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateSalonRequest {
    private String name;
    private String description;
    private String address;
    private String phone;
    private String imageUrl;
    private double latitude;
    private double longitude;
    private String openingHours;
    private List<String> tags;
}
