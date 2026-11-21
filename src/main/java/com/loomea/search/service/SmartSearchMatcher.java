package com.loomea.search.service;

import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.SalonService;
import com.loomea.search.dto.SmartSearchCriteria;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Matches salons against validated smart-search criteria (excluding availability). */
public final class SmartSearchMatcher {

    private SmartSearchMatcher() {}

    public static boolean matches(Salon salon, SmartSearchCriteria criteria) {
        if (!matchesLocation(salon, criteria.location())) {
            return false;
        }
        return !constrainsServices(criteria) || !matchingServices(salon, criteria).isEmpty();
    }

    public static boolean constrainsServices(SmartSearchCriteria criteria) {
        return criteria.service() != null || criteria.category() != null || criteria.maxPrice() != null;
    }

    /** Services that satisfy the service, category and price filters. */
    public static List<SalonService> matchingServices(Salon salon, SmartSearchCriteria criteria) {
        return salon.getServices().stream()
                .filter(s -> matchesServiceOrCategory(s, criteria))
                .filter(s -> criteria.maxPrice() == null || s.getPrice() <= criteria.maxPrice())
                .toList();
    }

    static boolean matchesLocation(Salon salon, String location) {
        if (location == null) {
            return true;
        }
        String address = normalize(salon.getAddress());
        String wanted = normalize(location);
        return !wanted.isEmpty() && address.contains(wanted);
    }

    private static boolean matchesServiceOrCategory(SalonService service, SmartSearchCriteria criteria) {
        if (criteria.service() != null) {
            String offered = normalize(service.getName());
            String wanted = normalize(criteria.service());
            return !offered.isEmpty() && (offered.contains(wanted) || wanted.contains(offered));
        }
        if (criteria.category() != null) {
            return criteria.category().equalsIgnoreCase(service.getCategory());
        }
        return true;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
