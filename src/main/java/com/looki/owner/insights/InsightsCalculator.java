package com.looki.owner.insights;

import com.looki.booking.entity.AppointmentStatus;
import com.looki.owner.insights.InsightsInput.OpeningHours;
import com.looki.owner.insights.InsightsInput.ServiceLine;
import com.looki.owner.insights.InsightsInput.Visit;
import com.looki.owner.insights.InsightsMetrics.ServiceStat;
import com.looki.owner.insights.InsightsMetrics.Slot;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Computes owner insights for the last {@value #PERIOD_DAYS} days (including
 * today) compared with the {@value #PERIOD_DAYS} days before.
 */
public final class InsightsCalculator {

    public static final int PERIOD_DAYS = 28;
    static final int MIN_BOOKINGS = 5;
    static final int UPCOMING_DAYS = 7;
    static final int TOP_SERVICES = 3;
    static final int MIN_OPEN_MINUTES_FOR_SLOT = 60;

    // Same defaults the booking service uses when a salon has no working hours.
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(20, 0);

    private static final LocalTime NOON = LocalTime.NOON;
    private static final LocalTime EVENING = LocalTime.of(17, 0);
    private static final List<String> PARTS = List.of("morning", "afternoon", "evening");

    /** Slots a client actually held: cancelled and unconfirmed requests are excluded. */
    private static final Set<AppointmentStatus> BOOKED =
            EnumSet.of(AppointmentStatus.CONFIRMED, AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW);

    private InsightsCalculator() {}

    public static InsightsMetrics calculate(List<Visit> visits, List<OpeningHours> hours, LocalDate today) {
        LocalDate start = today.minusDays(PERIOD_DAYS - 1L);
        LocalDate previousStart = start.minusDays(PERIOD_DAYS);

        List<Visit> current = between(visits, start, today);
        List<Visit> previous = between(visits, previousStart, start.minusDays(1));

        int bookings = count(current, BOOKED);
        int previousBookings = count(previous, BOOKED);
        int completed = count(current, EnumSet.of(AppointmentStatus.COMPLETED));
        int cancellations = count(current, EnumSet.of(AppointmentStatus.CANCELLED));
        int noShows = count(current, EnumSet.of(AppointmentStatus.NO_SHOW));
        long revenue = Math.round(revenue(current));
        long previousRevenue = Math.round(revenue(previous));

        int upcoming = (int) visits.stream()
                .filter(v -> v.date().isAfter(today) && !v.date().isAfter(today.plusDays(UPCOMING_DAYS)))
                .filter(v -> v.status() == AppointmentStatus.PENDING || v.status() == AppointmentStatus.CONFIRMED)
                .count();

        Map<String, Slot> slots = slots(current, hours, start, today);

        return new InsightsMetrics(
                bookings + cancellations >= MIN_BOOKINGS,
                PERIOD_DAYS,
                start.toString(),
                today.toString(),
                bookings,
                previousBookings,
                changePercent(bookings, previousBookings),
                revenue,
                previousRevenue,
                changePercent(revenue, previousRevenue),
                completed,
                completed > 0 ? Math.round((double) revenue / completed) : null,
                cancellations,
                percent(cancellations, bookings + cancellations),
                noShows,
                percent(noShows, completed + noShows),
                returningClientRate(visits, current, start),
                upcoming,
                topServices(current),
                slots.get("busiest"),
                slots.get("quietest"));
    }

    private static List<Visit> between(List<Visit> visits, LocalDate from, LocalDate to) {
        return visits.stream().filter(v -> !v.date().isBefore(from) && !v.date().isAfter(to)).toList();
    }

    private static int count(List<Visit> visits, Set<AppointmentStatus> statuses) {
        return (int) visits.stream().filter(v -> statuses.contains(v.status())).count();
    }

    private static double revenue(List<Visit> visits) {
        return visits.stream()
                .filter(v -> v.status() == AppointmentStatus.COMPLETED)
                .mapToDouble(Visit::totalPrice)
                .sum();
    }

    static Integer percent(long part, long whole) {
        return whole > 0 ? (int) Math.round(100.0 * part / whole) : null;
    }

    static Integer changePercent(long now, long before) {
        return before > 0 ? (int) Math.round(100.0 * (now - before) / before) : null;
    }

    private static Integer returningClientRate(List<Visit> all, List<Visit> current, LocalDate start) {
        Set<UUID> clients = new HashSet<>();
        current.stream().filter(v -> BOOKED.contains(v.status())).forEach(v -> clients.add(v.clientId()));
        if (clients.isEmpty()) {
            return null;
        }
        Set<UUID> seenBefore = new HashSet<>();
        all.stream()
                .filter(v -> v.date().isBefore(start) && BOOKED.contains(v.status()))
                .forEach(v -> seenBefore.add(v.clientId()));
        long returning = clients.stream().filter(seenBefore::contains).count();
        return percent(returning, clients.size());
    }

    private static List<ServiceStat> topServices(List<Visit> current) {
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, Double> revenues = new HashMap<>();
        for (Visit visit : current) {
            if (visit.status() != AppointmentStatus.CONFIRMED && visit.status() != AppointmentStatus.COMPLETED) {
                continue;
            }
            for (ServiceLine line : visit.services()) {
                if (line.name() == null || line.name().isBlank()) {
                    continue;
                }
                counts.computeIfAbsent(line.name(), k -> new int[1])[0]++;
                if (visit.status() == AppointmentStatus.COMPLETED) {
                    revenues.merge(line.name(), line.price(), Double::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .map(e -> new ServiceStat(e.getKey(), e.getValue()[0],
                        Math.round(revenues.getOrDefault(e.getKey(), 0.0))))
                .sorted(Comparator.comparingInt(ServiceStat::bookings).reversed()
                        .thenComparing(Comparator.comparingLong(ServiceStat::revenue).reversed())
                        .thenComparing(ServiceStat::name))
                .limit(TOP_SERVICES)
                .toList();
    }

    /** Busiest and quietest open weekly slots, by bookings that started in them. */
    private static Map<String, Slot> slots(List<Visit> current, List<OpeningHours> hours,
                                           LocalDate start, LocalDate today) {
        int[][] counts = new int[7][PARTS.size()];
        for (Visit visit : current) {
            if (BOOKED.contains(visit.status())) {
                counts[visit.date().getDayOfWeek().getValue() - 1][partOf(visit.startTime())]++;
            }
        }

        Map<Integer, OpeningHours> byDay = new HashMap<>();
        hours.forEach(h -> byDay.put(h.dayOfWeek(), h));
        long weeks = ChronoUnit.DAYS.between(start, today.plusDays(1)) / 7;
        if (weeks == 0) {
            return Map.of();
        }

        Slot busiest = null;
        Slot quietest = null;
        for (int day = 0; day < 7; day++) {
            for (int part = 0; part < PARTS.size(); part++) {
                if (openMinutes(byDay.get(day), part) < MIN_OPEN_MINUTES_FOR_SLOT) {
                    continue;
                }
                double perWeek = Math.round(10.0 * counts[day][part] / weeks) / 10.0;
                Slot slot = new Slot(DayOfWeek.of(day + 1).getValue(), PARTS.get(part), perWeek);
                if (busiest == null || perWeek > busiest.bookingsPerWeek()) {
                    busiest = slot;
                }
                if (quietest == null || perWeek < quietest.bookingsPerWeek()) {
                    quietest = slot;
                }
            }
        }
        Map<String, Slot> result = new HashMap<>();
        if (busiest != null && busiest.bookingsPerWeek() > 0) {
            result.put("busiest", busiest);
            if (quietest.bookingsPerWeek() < busiest.bookingsPerWeek()) {
                result.put("quietest", quietest);
            }
        }
        return result;
    }

    private static int partOf(LocalTime time) {
        if (time.isBefore(NOON)) {
            return 0;
        }
        return time.isBefore(EVENING) ? 1 : 2;
    }

    private static long openMinutes(OpeningHours hours, int part) {
        if (hours != null && (hours.closed() || hours.open() == null || hours.close() == null)) {
            return 0;
        }
        LocalTime open = hours == null ? DEFAULT_OPEN : hours.open();
        LocalTime close = hours == null ? DEFAULT_CLOSE : hours.close();
        LocalTime partStart = part == 0 ? LocalTime.MIN : part == 1 ? NOON : EVENING;
        LocalTime partEnd = part == 0 ? NOON : part == 1 ? EVENING : LocalTime.MAX;
        LocalTime from = open.isAfter(partStart) ? open : partStart;
        LocalTime to = close.isBefore(partEnd) ? close : partEnd;
        return from.isBefore(to) ? ChronoUnit.MINUTES.between(from, to) : 0;
    }
}
