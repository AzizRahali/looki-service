package com.loomea.owner.dto;

import com.loomea.booking.dto.BookingDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DashboardDto {
    private String salonId;
    private String salonName;
    private int todayAppointments;
    private int upcomingAppointments;
    private int completedAppointments;
    private int cancelledAppointments;
    private double todayRevenue;
    private double totalRevenue;
    private List<BookingDto> todayBookings;
}
