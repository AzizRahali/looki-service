package com.looki.booking.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Getter
@Builder
public class BookingDto {
    private String id;
    private String salonId;
    private String salonName;
    private String salonAddress;
    private String salonImageUrl;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String paymentMethod;
    /** AWAITING_PAYMENT | PAID | REFUNDED for card bookings, null for pay-at-salon. */
    private String paymentStatus;
    private String notes;
    private double totalPrice;
    private String clientId;
    private String clientName;
    private String clientAvatarUrl;
    private List<BookingServiceDto> services;
    /** Set for risky-tier users — they must confirm attendance. */
    private boolean requiresClientConfirmation;
    /** ISO-8601 timestamp of attendance confirmation, or {@code null}. */
    private String clientConfirmedAt;
    /** Client's reliability score — visible to salon owners. */
    private int clientReliabilityScore;
    /** Client's reliability tier label — visible to salon owners. */
    private String clientReliabilityTier;
    @Getter
    @Builder
    public static class BookingServiceDto {
        private String serviceName;
        private double price;
        private int durationMinutes;
    }
}
