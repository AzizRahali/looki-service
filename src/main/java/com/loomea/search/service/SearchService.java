package com.loomea.search.service;

import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.common.exception.BadRequestException;
import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.SalonService;
import com.loomea.salon.entity.WorkingHours;
import com.loomea.salon.repository.SalonRepository;
import com.loomea.salon.repository.WorkingHoursRepository;
import com.loomea.salon.service.SalonServiceImpl;
import com.loomea.search.ai.ModalSearchParser;
import com.loomea.search.ai.SmartSearchInterpretation;
import com.loomea.search.dto.SmartSearchCriteria;
import com.loomea.search.dto.SmartSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchService {

    public static final int MAX_SMART_QUERY_LENGTH = 300;

    private static final int SLOT_STEP_MINUTES = 30;
    private static final int DEFAULT_DURATION_MINUTES = 60;
    // Assumed business hours for salons that have not configured working hours.
    private static final LocalTime DEFAULT_OPEN = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_CLOSE = LocalTime.of(21, 0);

    private final SalonRepository salonRepository;
    private final SalonServiceImpl salonMapper;
    private final WorkingHoursRepository workingHoursRepository;
    private final AppointmentRepository appointmentRepository;
    private final ModalSearchParser modalSearchParser;

    @Value("${app.time-zone:Africa/Tunis}")
    private String timeZone;

    /**
     * Interprets a natural-language query with AI, then filters salons from the
     * database. Falls back to keyword search when AI is unavailable.
     * Not transactional: the AI call can be slow and must not hold a DB connection.
     */
    public SmartSearchResponse smartSearch(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() > MAX_SMART_QUERY_LENGTH) {
            throw new BadRequestException(
                    "Search query must be at most " + MAX_SMART_QUERY_LENGTH + " characters");
        }
        if (trimmed.isEmpty()) {
            return keywordResponse(trimmed);
        }

        Optional<SmartSearchInterpretation> interpretation = modalSearchParser.parse(trimmed);
        if (interpretation.isEmpty()) {
            return keywordResponse(trimmed);
        }

        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone);
        SmartSearchCriteria criteria = SmartSearchCriteriaResolver.resolve(interpretation.get(), today, now);
        if (criteria.isEmpty()) {
            return SmartSearchResponse.builder()
                    .aiUsed(true)
                    .filters(SmartSearchResponse.Filters.from(criteria))
                    .salons(search(trimmed))
                    .build();
        }

        List<SalonDto> salons = findMatching(criteria, today, now);
        if (salons.isEmpty() && criteria.service() != null && !anySalonOffers(criteria.service())) {
            // No salon lists this exact service name; widen to its category.
            criteria = new SmartSearchCriteria(null, criteria.category(), criteria.location(),
                    criteria.maxPrice(), criteria.date(), criteria.timeFrom(), criteria.timeTo());
            salons = findMatching(criteria, today, now);
        }

        return SmartSearchResponse.builder()
                .aiUsed(true)
                .filters(SmartSearchResponse.Filters.from(criteria))
                .salons(salons)
                .build();
    }

    private boolean anySalonOffers(String service) {
        SmartSearchCriteria serviceOnly = new SmartSearchCriteria(service, null, null, null, null, null, null);
        return salonRepository.findAll().stream()
                .anyMatch(salon -> !SmartSearchMatcher.matchingServices(salon, serviceOnly).isEmpty());
    }

    private List<SalonDto> findMatching(SmartSearchCriteria criteria, LocalDate today, LocalTime now) {
        return salonRepository.findAll().stream()
                .filter(salon -> SmartSearchMatcher.matches(salon, criteria))
                .filter(salon -> criteria.date() == null
                        || hasAvailability(salon, criteria, today, now))
                .map(salonMapper::toDto)
                .toList();
    }

    private SmartSearchResponse keywordResponse(String query) {
        return SmartSearchResponse.builder()
                .aiUsed(false)
                .salons(search(query))
                .build();
    }

    private boolean hasAvailability(Salon salon, SmartSearchCriteria criteria, LocalDate today, LocalTime now) {
        LocalDate date = criteria.date();
        int dayOfWeek = date.getDayOfWeek().getValue() - 1;
        Optional<WorkingHours> hours = workingHoursRepository.findBySalonIdAndDayOfWeek(salon.getId(), dayOfWeek);
        if (hours.isPresent() && hours.get().isClosed()) {
            return false;
        }
        LocalTime open = hours.map(WorkingHours::getOpenTime).orElse(DEFAULT_OPEN);
        LocalTime close = hours.map(WorkingHours::getCloseTime).orElse(DEFAULT_CLOSE);
        if (open == null || close == null || !open.isBefore(close)) {
            return false;
        }

        int duration = requiredDuration(salon, criteria);
        LocalTime first = criteria.timeFrom() != null ? criteria.timeFrom() : open;
        LocalTime last = criteria.timeTo() != null ? criteria.timeTo() : close.minusMinutes(duration);

        for (LocalTime start = first; !start.isAfter(last); ) {
            LocalTime end = start.plusMinutes(duration);
            boolean inFuture = !date.equals(today) || !start.isBefore(now);
            boolean withinHours = !start.isBefore(open) && !end.isAfter(close) && end.isAfter(start);
            if (inFuture && withinHours
                    && appointmentRepository.countConflicts(salon.getId(), date, start, end) == 0) {
                return true;
            }
            LocalTime next = start.plusMinutes(SLOT_STEP_MINUTES);
            if (!next.isAfter(start)) {
                break;
            }
            start = next;
        }
        return false;
    }

    private int requiredDuration(Salon salon, SmartSearchCriteria criteria) {
        List<SalonService> services = SmartSearchMatcher.constrainsServices(criteria)
                ? SmartSearchMatcher.matchingServices(salon, criteria)
                : salon.getServices();
        return services.stream()
                .mapToInt(SalonService::getDurationMinutes)
                .filter(minutes -> minutes > 0)
                .min()
                .orElse(DEFAULT_DURATION_MINUTES);
    }

    @Transactional(readOnly = true)
    public List<SalonDto> search(String query) {
        if (query == null || query.isBlank()) {
            return salonRepository.findAll().stream().map(salonMapper::toDto).toList();
        }
        return salonRepository.search(query.trim()).stream().map(salonMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDto> byCategory(String category) {
        return salonRepository.findByServiceCategory(category).stream().map(salonMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDto> searchAvailable(LocalDate date, LocalTime time, int durationMinutes) {
        // dayOfWeek: Monday=0 … Sunday=6 (matching working hours convention)
        int dayOfWeek = date.getDayOfWeek().getValue() - 1;
        LocalTime endTime = time.plusMinutes(durationMinutes);

        return salonRepository.findAll().stream()
                .filter(salon -> isAvailable(salon, date, dayOfWeek, time, endTime))
                .map(salonMapper::toDto)
                .toList();
    }

    private boolean isAvailable(Salon salon, LocalDate date, int dayOfWeek,
                                 LocalTime startTime, LocalTime endTime) {
        // Check working hours
        Optional<WorkingHours> wh = workingHoursRepository
                .findBySalonIdAndDayOfWeek(salon.getId(), dayOfWeek);
        if (wh.isPresent()) {
            if (wh.get().isClosed()) return false;
            if (startTime.isBefore(wh.get().getOpenTime())) return false;
            if (endTime.isAfter(wh.get().getCloseTime())) return false;
        }
        // Check no conflicting bookings
        long conflicts = appointmentRepository.countConflicts(
                salon.getId(), date, startTime, endTime);
        return conflicts == 0;
    }
}
