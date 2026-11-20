package com.looki.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@Builder
public class TimeSlotDto {
    private LocalTime time;
    private String status; // AVAILABLE | LIMITED | BOOKED
}
