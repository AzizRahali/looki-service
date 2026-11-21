package com.looki.search.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Raw, untrusted filters returned by the AI parser. Validated before use. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SmartSearchInterpretation(
        String service,
        String category,
        String location,
        Double maxPrice,
        String day,
        String time,
        boolean parsed) {
}
