package com.loomea.owner.insights;

import com.loomea.booking.entity.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Database-independent inputs for {@link InsightsCalculator}. */
public final class InsightsInput {

    private InsightsInput() {}

    public record ServiceLine(String name, double price) {}

    public record Visit(UUID clientId, LocalDate date, LocalTime startTime,
                        AppointmentStatus status, double totalPrice, List<ServiceLine> services) {}

    /** {@code dayOfWeek}: 0 = Monday … 6 = Sunday, matching {@code WorkingHours}. */
    public record OpeningHours(int dayOfWeek, LocalTime open, LocalTime close, boolean closed) {}
}
