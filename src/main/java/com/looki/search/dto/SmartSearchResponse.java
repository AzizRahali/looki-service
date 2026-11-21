package com.looki.search.dto;

import com.looki.salon.dto.SalonDto;
import lombok.Builder;
import lombok.Getter;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Builder
public class SmartSearchResponse {

    /** True when the AI interpreted the query; false when keyword search was used. */
    private boolean aiUsed;
    private Filters filters;
    private List<SalonDto> salons;

    public record Filters(
            String service,
            String category,
            String location,
            Double maxPrice,
            String date,
            String timeFrom,
            String timeTo) {

        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

        public static Filters from(SmartSearchCriteria c) {
            return new Filters(
                    c.service(),
                    c.category(),
                    c.location(),
                    c.maxPrice(),
                    c.date() == null ? null : c.date().toString(),
                    c.timeFrom() == null ? null : c.timeFrom().format(TIME),
                    c.timeTo() == null ? null : c.timeTo().format(TIME));
        }
    }
}
