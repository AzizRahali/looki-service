package com.loomea.search.service;

import com.loomea.booking.repository.AppointmentRepository;
import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.WorkingHours;
import com.loomea.salon.repository.SalonRepository;
import com.loomea.salon.repository.WorkingHoursRepository;
import com.loomea.salon.service.SalonServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final SalonRepository salonRepository;
    private final SalonServiceImpl salonMapper;
    private final WorkingHoursRepository workingHoursRepository;
    private final AppointmentRepository appointmentRepository;

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
