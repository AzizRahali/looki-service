package com.looki.booking.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WaitlistDto {
    private String id;
    private String salonId;
    private String salonName;
    private String date;
    private String status;
    private String createdAt;
}
