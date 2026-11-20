package com.looki.booking.service;

import com.looki.auth.entity.User;
import com.looki.auth.repository.UserRepository;
import com.looki.booking.dto.WaitlistDto;
import com.looki.booking.entity.WaitlistEntry;
import com.looki.booking.entity.WaitlistStatus;
import com.looki.booking.repository.WaitlistRepository;
import com.looki.common.exception.BadRequestException;
import com.looki.common.exception.ResourceNotFoundException;
import com.looki.salon.entity.Salon;
import com.looki.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final UserRepository userRepository;
    private final SalonRepository salonRepository;

    @Transactional
    public WaitlistDto join(UUID userId, UUID salonId, LocalDate date) {
        if (waitlistRepository.findByUserIdAndSalonIdAndDate(userId, salonId, date).isPresent()) {
            throw new BadRequestException("Already on waitlist for this date");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Salon salon = salonRepository.findById(salonId)
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "id", salonId));

        WaitlistEntry entry = WaitlistEntry.builder()
                .user(user).salon(salon).date(date).build();
        return toDto(waitlistRepository.save(entry));
    }

    @Transactional
    public void leave(UUID entryId, UUID userId) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("WaitlistEntry", "id", entryId));
        if (!entry.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        waitlistRepository.delete(entry);
    }

    @Transactional(readOnly = true)
    public List<WaitlistDto> getMyWaiting(UUID userId) {
        return waitlistRepository.findByUserIdAndStatus(userId, WaitlistStatus.WAITING)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WaitlistDto> getMyAll(UUID userId) {
        List<WaitlistEntry> waiting  = waitlistRepository.findByUserIdAndStatus(userId, WaitlistStatus.WAITING);
        List<WaitlistEntry> notified = waitlistRepository.findByUserIdAndStatus(userId, WaitlistStatus.NOTIFIED);
        waiting.addAll(notified);
        return waiting.stream().map(this::toDto).collect(Collectors.toList());
    }

    private WaitlistDto toDto(WaitlistEntry e) {
        return WaitlistDto.builder()
                .id(e.getId().toString())
                .salonId(e.getSalon().getId().toString())
                .salonName(e.getSalon().getName())
                .date(e.getDate().toString())
                .status(e.getStatus().name())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .build();
    }
}
