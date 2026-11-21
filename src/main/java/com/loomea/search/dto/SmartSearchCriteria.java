package com.loomea.search.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Validated smart-search filters. Every field is optional. When a time range is
 * present, appointments must start between {@code timeFrom} and {@code timeTo}
 * inclusive; an exact time has equal bounds.
 */
public record SmartSearchCriteria(
        String service,
        String category,
        String location,
        Double maxPrice,
        LocalDate date,
        LocalTime timeFrom,
        LocalTime timeTo) {

    public static final SmartSearchCriteria EMPTY =
            new SmartSearchCriteria(null, null, null, null, null, null, null);

    public boolean isEmpty() {
        return service == null && category == null && location == null
                && maxPrice == null && date == null;
    }
}
