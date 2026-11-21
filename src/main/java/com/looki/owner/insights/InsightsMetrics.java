package com.looki.owner.insights;

import java.util.List;

/** Numbers computed from a salon's bookings; the source of truth for owner insights. */
public record InsightsMetrics(
        boolean enoughData,
        int periodDays,
        String periodStart,
        String periodEnd,
        int bookings,
        int previousBookings,
        Integer bookingsChangePercent,
        long revenue,
        long previousRevenue,
        Integer revenueChangePercent,
        int completed,
        Long averageBookingValue,
        int cancellations,
        Integer cancellationRate,
        int noShows,
        Integer noShowRate,
        Integer returningClientRate,
        int upcomingBookings,
        List<ServiceStat> topServices,
        Slot busiestSlot,
        Slot quietestSlot) {

    public record ServiceStat(String name, int bookings, long revenue) {}

    /**
     * A recurring weekly time slot.
     *
     * @param dayOfWeek       1 = Monday … 7 = Sunday
     * @param partOfDay       "morning", "afternoon" or "evening"
     * @param bookingsPerWeek average bookings starting in this slot, one decimal
     */
    public record Slot(int dayOfWeek, String partOfDay, double bookingsPerWeek) {}
}
