package com.loomea.search.service;

import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.SalonService;
import com.loomea.search.ai.SmartSearchInterpretation;
import com.loomea.search.dto.SmartSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SmartSearchTest {

    // A Friday.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    private static SmartSearchInterpretation raw(String service, String category, String location,
                                                 Double maxPrice, String day, String time) {
        return new SmartSearchInterpretation(service, category, location, maxPrice, day, time, true);
    }

    @Test
    void resolvesRelativeDaysAndWeekdays() {
        LocalTime noon = LocalTime.NOON;
        assertThat(resolve(raw(null, null, null, null, "tomorrow", null), noon).date())
                .isEqualTo(TODAY.plusDays(1));
        assertThat(resolve(raw(null, null, null, null, "Saturday", null), noon).date())
                .isEqualTo(LocalDate.of(2026, 11, 21));
        assertThat(resolve(raw(null, null, null, null, "friday", null), noon).date())
                .isEqualTo(TODAY);
    }

    @Test
    void dropsValuesOutsideTheCatalogOrAllowedRanges() {
        SmartSearchCriteria criteria = resolve(
                raw("Teeth whitening", "Dentistry", " ", -5.0, "2026-11-19", "25:00"), LocalTime.NOON);

        assertThat(criteria.isEmpty()).isTrue();
        assertThat(criteria.timeFrom()).isNull();
        assertThat(resolve(raw(null, null, null, null, TODAY.plusDays(61).toString(), null), LocalTime.NOON)
                .date()).isNull();
    }

    @Test
    void serviceDeterminesItsCatalogCategory() {
        SmartSearchCriteria criteria = resolve(
                raw("manicure", null, null, null, null, null), LocalTime.NOON);
        SmartSearchCriteria conflicting = resolve(
                raw("Eyebrow Threading", "Lashes", null, null, null, null), LocalTime.NOON);

        assertThat(criteria.service()).isEqualTo("Manicure");
        assertThat(criteria.category()).isEqualTo("Nails");
        assertThat(conflicting.category()).isEqualTo("Skin & Face");
    }

    @Test
    void timeWithoutDayUsesNextOccurrence() {
        SmartSearchCriteria laterToday = resolve(raw(null, null, null, null, null, "15:00"), LocalTime.of(10, 0));
        assertThat(laterToday.date()).isEqualTo(TODAY);
        assertThat(laterToday.timeFrom()).isEqualTo(LocalTime.of(15, 0));
        assertThat(laterToday.timeTo()).isEqualTo(LocalTime.of(15, 0));

        SmartSearchCriteria morningPassed = resolve(raw(null, null, null, null, null, "morning"), LocalTime.of(14, 0));
        assertThat(morningPassed.date()).isEqualTo(TODAY.plusDays(1));
        assertThat(morningPassed.timeFrom()).isEqualTo(LocalTime.of(8, 0));
    }

    @Test
    void matchesLocationIgnoringAccentsAndAppliesServicePrice() {
        Salon salon = salon("12 Rue de la Liberté, La Marsa",
                service("Haircut (Women's)", "Hair", 35),
                service("Balayage", "Hair", 120));

        SmartSearchCriteria affordableCut = new SmartSearchCriteria(
                "Haircut (Women's)", "Hair", "la marsa", 40.0, null, null, null);
        SmartSearchCriteria tooCheap = new SmartSearchCriteria(
                "Balayage", "Hair", "La Marsa", 100.0, null, null, null);
        SmartSearchCriteria elsewhere = new SmartSearchCriteria(
                null, "Hair", "Sousse", null, null, null, null);
        SmartSearchCriteria accentedStreet = new SmartSearchCriteria(
                null, null, "liberte", null, null, null, null);

        assertThat(SmartSearchMatcher.matches(salon, affordableCut)).isTrue();
        assertThat(SmartSearchMatcher.matches(salon, tooCheap)).isFalse();
        assertThat(SmartSearchMatcher.matches(salon, elsewhere)).isFalse();
        assertThat(SmartSearchMatcher.matches(salon, accentedStreet)).isTrue();
    }

    private static SmartSearchCriteria resolve(SmartSearchInterpretation raw, LocalTime now) {
        return SmartSearchCriteriaResolver.resolve(raw, TODAY, now);
    }

    private static Salon salon(String address, SalonService... services) {
        Salon salon = Salon.builder().id(UUID.randomUUID()).name("Test salon").address(address).build();
        for (SalonService service : services) {
            salon.addService(service);
        }
        return salon;
    }

    private static SalonService service(String name, String category, double price) {
        return SalonService.builder()
                .id(UUID.randomUUID())
                .name(name)
                .category(category)
                .price(price)
                .durationMinutes(45)
                .build();
    }
}
