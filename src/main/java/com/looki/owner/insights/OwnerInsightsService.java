package com.looki.owner.insights;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.looki.booking.repository.AppointmentRepository;
import com.looki.common.ai.ModalClient;
import com.looki.common.exception.BadRequestException;
import com.looki.owner.insights.InsightsInput.OpeningHours;
import com.looki.owner.insights.InsightsInput.ServiceLine;
import com.looki.owner.insights.InsightsInput.Visit;
import com.looki.salon.repository.SalonRepository;
import com.looki.salon.repository.WorkingHoursRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Owner insights: numbers computed from the database, plus an optional AI-written
 * summary and suggestions based only on those numbers.
 * Not transactional: the AI call can be slow and must not hold a DB connection.
 */
@Service
public class OwnerInsightsService {

    private static final Set<String> LANGUAGES = Set.of("en", "fr", "ar");
    private static final int CACHE_SIZE = 200;

    private final SalonRepository salonRepository;
    private final AppointmentRepository appointmentRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final ModalClient modalClient;
    private final String url;
    private final ZoneId zone;

    private final Map<String, AiText> cache = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, AiText> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    public OwnerInsightsService(SalonRepository salonRepository,
                                AppointmentRepository appointmentRepository,
                                WorkingHoursRepository workingHoursRepository,
                                ModalClient modalClient,
                                @Value("${app.owner-insights.modal-url:}") String url,
                                @Value("${app.time-zone:Africa/Tunis}") String timeZone) {
        this.salonRepository = salonRepository;
        this.appointmentRepository = appointmentRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.modalClient = modalClient;
        this.url = url;
        this.zone = ZoneId.of(timeZone);
    }

    public OwnerInsightsDto getInsights(UUID salonId, UUID ownerId, String lang) {
        salonRepository.findByIdAndOwnerId(salonId, ownerId)
                .orElseThrow(() -> new BadRequestException("Salon not found or you don't own it"));
        String language = normalizeLanguage(lang);

        List<Visit> visits = appointmentRepository.findAllBySalonId(salonId).stream()
                .map(a -> new Visit(
                        a.getUser().getId(),
                        a.getDate(),
                        a.getStartTime(),
                        a.getStatus(),
                        a.getTotalPrice(),
                        a.getServices().stream()
                                .map(s -> new ServiceLine(s.getServiceName(), s.getPrice()))
                                .toList()))
                .toList();
        List<OpeningHours> hours = workingHoursRepository.findBySalonIdOrderByDayOfWeek(salonId).stream()
                .map(h -> new OpeningHours(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(), h.isClosed()))
                .toList();

        InsightsMetrics metrics = InsightsCalculator.calculate(visits, hours, LocalDate.now(zone));
        AiText ai = metrics.enoughData() ? writeWithAi(salonId, language, metrics) : AiText.NONE;

        return OwnerInsightsDto.builder()
                .metrics(metrics)
                .aiUsed(ai.summary() != null || !ai.suggestions().isEmpty())
                .summary(ai.summary())
                .suggestions(ai.suggestions())
                .build();
    }

    private AiText writeWithAi(UUID salonId, String language, InsightsMetrics metrics) {
        List<String> facts = InsightsFacts.build(metrics);
        String cacheKey = salonId + "|" + language + "|" + facts.hashCode();
        AiText cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Optional<AiResponse> response = modalClient
                .post(url, Map.of("language", language, "facts", facts), AiResponse.class)
                .filter(AiResponse::parsed);
        if (response.isEmpty()) {
            return AiText.NONE;
        }
        String summary = InsightsFacts.validate(response.get().summary(), facts, InsightsFacts.MAX_SUMMARY_LENGTH);
        List<String> suggestions = Optional.ofNullable(response.get().suggestions()).orElse(List.of()).stream()
                .map(s -> InsightsFacts.validate(s, facts, InsightsFacts.MAX_SUGGESTION_LENGTH))
                .filter(s -> s != null)
                .limit(InsightsFacts.MAX_SUGGESTIONS)
                .toList();
        AiText text = new AiText(summary, suggestions);
        cache.put(cacheKey, text);
        return text;
    }

    public static String normalizeLanguage(String lang) {
        if (lang == null) {
            return "en";
        }
        String code = lang.trim().toLowerCase(Locale.ROOT);
        if (code.startsWith("ar")) {
            return "ar";
        }
        return LANGUAGES.contains(code) ? code : "en";
    }

    private record AiText(String summary, List<String> suggestions) {
        static final AiText NONE = new AiText(null, List.of());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AiResponse(String summary, List<String> suggestions, boolean parsed) {}
}
