package com.loomea.owner.insights;

import com.loomea.common.ai.GroundingCheck;
import com.loomea.owner.insights.InsightsMetrics.ServiceStat;
import com.loomea.owner.insights.InsightsMetrics.Slot;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Builds the facts the AI may use, and checks that AI-written text does not
 * mention any number that is not among them.
 */
public final class InsightsFacts {

    static final int MAX_SUMMARY_LENGTH = 400;
    static final int MAX_SUGGESTION_LENGTH = 250;
    static final int MAX_SUGGESTIONS = 3;

    private InsightsFacts() {}

    public static List<String> build(InsightsMetrics m) {
        List<String> facts = new ArrayList<>();
        facts.add("Period: the last " + m.periodDays() + " days, compared with the "
                + m.periodDays() + " days before.");
        facts.add("Bookings: " + m.bookings() + " (previous period: " + m.previousBookings() + ")"
                + change(m.bookingsChangePercent()) + ".");
        facts.add("Revenue from completed appointments: " + m.revenue() + " TND (previous period: "
                + m.previousRevenue() + " TND)" + change(m.revenueChangePercent()) + ".");
        if (m.averageBookingValue() != null) {
            facts.add("Average value of a completed appointment: " + m.averageBookingValue() + " TND.");
        }
        if (m.cancellationRate() != null) {
            facts.add("Cancellations: " + m.cancellations() + " (" + m.cancellationRate() + "% of bookings).");
        }
        if (m.noShowRate() != null) {
            facts.add("No-shows: " + m.noShows() + " (" + m.noShowRate()
                    + "% of appointments that were due).");
        }
        if (m.returningClientRate() != null) {
            facts.add("Returning clients: " + m.returningClientRate()
                    + "% of this period's clients had booked before.");
        }
        if (!m.topServices().isEmpty()) {
            facts.add("Most booked services: " + m.topServices().stream()
                    .map(InsightsFacts::service)
                    .collect(Collectors.joining(", ")) + ".");
        }
        if (m.busiestSlot() != null) {
            facts.add("Busiest time: " + slot(m.busiestSlot()) + ".");
        }
        if (m.quietestSlot() != null) {
            facts.add("Quietest open time: " + slot(m.quietestSlot()) + ".");
        }
        facts.add("Upcoming bookings in the next 7 days: " + m.upcomingBookings() + ".");
        return facts;
    }

    /** Returns the text if it is safe to show, otherwise {@code null}. */
    public static String validate(String text, List<String> facts, int maxLength) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.isEmpty() || trimmed.length() > maxLength) {
            return null;
        }
        return GroundingCheck.isGrounded(trimmed, facts) ? trimmed : null;
    }

    private static String change(Integer percent) {
        if (percent == null) {
            return "";
        }
        if (percent == 0) {
            return ", no change";
        }
        return percent > 0 ? ", up " + percent + "%" : ", down " + Math.abs(percent) + "%";
    }

    private static String service(ServiceStat s) {
        return s.name() + " (" + s.bookings() + (s.bookings() == 1 ? " booking" : " bookings") + ")";
    }

    private static String slot(Slot s) {
        return DayOfWeek.of(s.dayOfWeek()).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + s.partOfDay()
                + " (about " + format(s.bookingsPerWeek()) + " bookings per week)";
    }

    static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
