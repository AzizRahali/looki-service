package com.loomea.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class CreateBookingRequest {

    @NotNull(message = "Salon ID is required")
    private String salonId;

    @NotNull(message = "Date is required")
    private LocalDate date;

    @NotNull(message = "Start time is required")
    private LocalTime startTime;

    @NotEmpty(message = "Select at least one service")
    private List<String> serviceIds;

    private String paymentMethod;  // PAY_AT_SALON | PAY_WITH_CARD
    private String notes;
    private String staffMemberId; // optional — assign to a specific stylist
}
