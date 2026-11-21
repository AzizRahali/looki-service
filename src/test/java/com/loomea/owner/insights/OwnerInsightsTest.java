package com.loomea.owner.insights;

import com.loomea.booking.entity.AppointmentStatus;
import com.loomea.owner.insights.InsightsInput.OpeningHours;
import com.loomea.owner.insights.InsightsInput.ServiceLine;
import com.loomea.owner.insights.InsightsInput.Visit;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.loomea.booking.entity.AppointmentStatus.CANCELLED;
import static com.loomea.booking.entity.AppointmentStatus.COMPLETED;
import static com.loomea.booking.entity.AppointmentStatus.CONFIRMED;
import static com.loomea.booking.entity.AppointmentStatus.NO_SHOW;
import static org.assertj.core.api.Assertions.assertThat;

class OwnerInsightsTest {

    // A Friday. The current period is 2026-10-24 .. 2026-11-20 (four of each weekday).
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static Visit visit(UUID client, LocalDate date, int hour, AppointmentStatus status, double price) {
        return new Visit(client, date, LocalTime.of(hour, 0), status, price,
                List.of(new ServiceLine("Haircut (Women's)", price)));
    }

    @Test
    void comparesPeriodsAndComputesRates() {
        List<Visit> visits = new ArrayList<>();
        // Previous period: 2 completed visits by Alice.
        visits.add(visit(ALICE, TODAY.minusDays(40), 10, COMPLETED, 40));
        visits.add(visit(ALICE, TODAY.minusDays(35), 10, COMPLETED, 40));
        // Current period: Alice returns, Bob is new.
        visits.add(visit(ALICE, TODAY.minusDays(10), 10, COMPLETED, 50));
        visits.add(visit(ALICE, TODAY.minusDays(5), 10, COMPLETED, 50));
        visits.add(visit(BOB, TODAY.minusDays(4), 15, NO_SHOW, 30));
        visits.add(visit(BOB, TODAY.minusDays(3), 15, CANCELLED, 30));
        visits.add(visit(BOB, TODAY.plusDays(2), 15, CONFIRMED, 30));
        visits.add(visit(BOB, TODAY.plusDays(9), 15, CONFIRMED, 30)); // beyond the 7-day window

        InsightsMetrics m = InsightsCalculator.calculate(visits, List.of(), TODAY);

        assertThat(m.enoughData()).isFalse(); // 3 bookings + 1 cancellation < 5
        assertThat(m.bookings()).isEqualTo(3);
        assertThat(m.previousBookings()).isEqualTo(2);
        assertThat(m.bookingsChangePercent()).isEqualTo(50);
        assertThat(m.revenue()).isEqualTo(100);
        assertThat(m.revenueChangePercent()).isEqualTo(25);
        assertThat(m.averageBookingValue()).isEqualTo(50);
        assertThat(m.cancellationRate()).isEqualTo(25);
        assertThat(m.noShowRate()).isEqualTo(33);
        assertThat(m.returningClientRate()).isEqualTo(50);
        assertThat(m.upcomingBookings()).isEqualTo(1);
    }

    @Test
    void findsBusiestAndQuietestOpenSlots() {
        List<Visit> visits = new ArrayList<>();
        // Four Saturday afternoons with two bookings each, one Tuesday morning booking.
        for (int week = 0; week < 4; week++) {
            LocalDate saturday = LocalDate.of(2026, 11, 14).minusWeeks(week);
            visits.add(visit(ALICE, saturday, 14, COMPLETED, 40));
            visits.add(visit(BOB, saturday, 15, COMPLETED, 40));
        }
        visits.add(visit(ALICE, LocalDate.of(2026, 11, 17), 10, COMPLETED, 40));

        // Open 09:00-18:00 Monday to Saturday; closed on Sunday.
        List<OpeningHours> hours = new ArrayList<>();
        for (int day = 0; day < 6; day++) {
            hours.add(new OpeningHours(day, LocalTime.of(9, 0), LocalTime.of(18, 0), false));
        }
        hours.add(new OpeningHours(6, null, null, true));

        InsightsMetrics m = InsightsCalculator.calculate(visits, hours, TODAY);

        assertThat(m.busiestSlot()).isEqualTo(new InsightsMetrics.Slot(6, "afternoon", 2.0));
        // Monday morning is the first open slot with no bookings (Sunday is closed).
        assertThat(m.quietestSlot()).isEqualTo(new InsightsMetrics.Slot(1, "morning", 0.0));
        assertThat(m.topServices()).containsExactly(
                new InsightsMetrics.ServiceStat("Haircut (Women's)", 9, 360));
    }

    @Test
    void rejectsAiTextWithNumbersNotInTheFacts() {
        List<String> facts = List.of("Bookings: 42 (previous period: 30), up 40%.",
                "Revenue from completed appointments: 1250 TND.",
                "Busiest time: Saturday afternoon (about 2.5 bookings per week).");

        assertThat(InsightsFacts.validate("Bookings are up 40% to 42.", facts, 400)).isNotNull();
        assertThat(InsightsFacts.validate("Revenue reached 1,250 TND.", facts, 400)).isNotNull();
        assertThat(InsightsFacts.validate("About 2,5 bookings on Saturday afternoons.", facts, 400)).isNotNull();
        assertThat(InsightsFacts.validate("الحجوزات زادت بـ ٤٠٪", facts, 400)).isNotNull();

        assertThat(InsightsFacts.validate("Bookings are up 45%.", facts, 400)).isNull();
        assertThat(InsightsFacts.validate("Offer 20% off on Tuesdays.", facts, 400)).isNull();
        assertThat(InsightsFacts.validate("الحجوزات زادت بـ ٤٥٪", facts, 400)).isNull();
        assertThat(InsightsFacts.validate("x".repeat(401), facts, 400)).isNull();
    }

    @Test
    void normalizesLanguageCodes() {
        assertThat(OwnerInsightsService.normalizeLanguage("ar_TN")).isEqualTo("ar");
        assertThat(OwnerInsightsService.normalizeLanguage("FR")).isEqualTo("fr");
        assertThat(OwnerInsightsService.normalizeLanguage("de")).isEqualTo("en");
    }
}
