package com.looki.search.service;

import com.looki.salon.catalog.ServiceCatalogData;
import com.looki.search.ai.SmartSearchInterpretation;
import com.looki.search.dto.SmartSearchCriteria;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns untrusted AI output into validated search criteria. Anything that does
 * not match the catalog or an allowed format is dropped rather than guessed.
 */
public final class SmartSearchCriteriaResolver {

    static final int MAX_DAYS_AHEAD = 60;
    static final double MAX_PRICE = 10_000;

    private static final Pattern EXACT_TIME = Pattern.compile("^([01]?\\d|2[0-3]):([0-5]\\d)$");

    private static final Map<String, LocalTime[]> TIME_WINDOWS = Map.of(
            "morning", new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(11, 30)},
            "afternoon", new LocalTime[]{LocalTime.of(12, 0), LocalTime.of(16, 30)},
            "evening", new LocalTime[]{LocalTime.of(17, 0), LocalTime.of(20, 30)});

    private static final Map<String, String> CATEGORIES = new HashMap<>();
    private static final Map<String, String> SERVICES = new HashMap<>();
    private static final Map<String, String> CATEGORY_OF_SERVICE = new HashMap<>();

    static {
        for (ServiceCatalogData.Level1 level1 : ServiceCatalogData.CATALOG) {
            CATEGORIES.put(key(level1.category()), level1.category());
            for (ServiceCatalogData.Level2 level2 : level1.services()) {
                SERVICES.put(key(level2.name()), level2.name());
                CATEGORY_OF_SERVICE.put(level2.name(), level1.category());
            }
        }
    }

    private SmartSearchCriteriaResolver() {}

    public static SmartSearchCriteria resolve(SmartSearchInterpretation raw, LocalDate today, LocalTime now) {
        if (raw == null) {
            return SmartSearchCriteria.EMPTY;
        }
        String service = raw.service() == null ? null : SERVICES.get(key(raw.service()));
        // The catalog defines which category a service belongs to.
        String category = service != null
                ? CATEGORY_OF_SERVICE.get(service)
                : raw.category() == null ? null : CATEGORIES.get(key(raw.category()));

        LocalDate date = resolveDay(raw.day(), today);
        LocalTime[] window = resolveTime(raw.time());
        if (window != null && date == null) {
            // A time without a day means the next occurrence of that time.
            date = window[1].isBefore(now) ? today.plusDays(1) : today;
        }

        return new SmartSearchCriteria(
                service,
                category,
                resolveLocation(raw.location()),
                resolvePrice(raw.maxPrice()),
                date,
                window == null ? null : window[0],
                window == null ? null : window[1]);
    }

    static LocalDate resolveDay(String day, LocalDate today) {
        if (day == null || day.isBlank()) {
            return null;
        }
        String value = key(day);
        LocalDate date = switch (value) {
            case "today" -> today;
            case "tomorrow" -> today.plusDays(1);
            case "day_after_tomorrow" -> today.plusDays(2);
            default -> {
                try {
                    DayOfWeek weekday = DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT));
                    yield today.with(TemporalAdjusters.nextOrSame(weekday));
                } catch (IllegalArgumentException notWeekday) {
                    try {
                        yield LocalDate.parse(value);
                    } catch (DateTimeParseException invalid) {
                        yield null;
                    }
                }
            }
        };
        if (date == null || date.isBefore(today) || date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            return null;
        }
        return date;
    }

    static LocalTime[] resolveTime(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        String value = key(time);
        LocalTime[] window = TIME_WINDOWS.get(value);
        if (window != null) {
            return window.clone();
        }
        Matcher matcher = EXACT_TIME.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        LocalTime exact = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        return new LocalTime[]{exact, exact};
    }

    private static String resolveLocation(String location) {
        if (location == null) {
            return null;
        }
        String trimmed = location.trim();
        return trimmed.length() < 2 || trimmed.length() > 80 ? null : trimmed;
    }

    private static Double resolvePrice(Double maxPrice) {
        if (maxPrice == null || maxPrice.isNaN() || maxPrice <= 0 || maxPrice > MAX_PRICE) {
            return null;
        }
        return maxPrice;
    }

    private static String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
