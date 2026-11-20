package com.looki.salon.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class SalonDto {
    private String id;
    private String name;
    private String description;
    private String address;
    private String phone;
    private String imageUrl;
    private double latitude;
    private double longitude;
    private double rating;
    private int reviewCount;
    private String openingHours;
    private List<String> tags;
    private List<ServiceDto> services;
}
