package com.looki.salon.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class ReviewDto {
    private String id;
    private String salonId;
    private String userId;
    private String userName;
    private String userAvatar;
    private int rating;
    private String comment;
    private String createdAt;
}
