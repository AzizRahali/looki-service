package com.loomea.search.ai;

import com.loomea.common.ai.ModalClient;
import com.loomea.salon.catalog.ServiceCatalogData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a natural-language query into structured filters using the Modal-hosted
 * model. Returns an empty result when AI is unavailable so callers can fall
 * back to keyword search.
 */
@Component
public class ModalSearchParser {

    private static final int CACHE_SIZE = 500;

    private static final List<String> CATEGORIES = ServiceCatalogData.CATALOG.stream()
            .map(ServiceCatalogData.Level1::category)
            .toList();

    private static final List<String> SERVICES = ServiceCatalogData.CATALOG.stream()
            .flatMap(level1 -> level1.services().stream())
            .map(ServiceCatalogData.Level2::name)
            .toList();

    private final ModalClient modalClient;
    private final String url;

    private final Map<String, SmartSearchInterpretation> cache =
            Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SmartSearchInterpretation> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    public ModalSearchParser(ModalClient modalClient,
                             @Value("${app.smart-search.modal-url:}") String url) {
        this.modalClient = modalClient;
        this.url = url;
    }

    public Optional<SmartSearchInterpretation> parse(String query) {
        if (!modalClient.isEnabled(url)) {
            return Optional.empty();
        }
        String cacheKey = query.trim().toLowerCase(Locale.ROOT);
        SmartSearchInterpretation cached = cache.get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SmartSearchInterpretation> result = modalClient
                .post(url, Map.of("query", query, "categories", CATEGORIES, "services", SERVICES),
                        SmartSearchInterpretation.class)
                .filter(SmartSearchInterpretation::parsed);
        result.ifPresent(r -> cache.put(cacheKey, r));
        return result;
    }
}
