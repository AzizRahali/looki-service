package com.loomea.salon.service;

import com.loomea.common.exception.ResourceNotFoundException;
import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.dto.ServiceDto;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.entity.SalonService;
import com.loomea.salon.repository.SalonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalonServiceImpl {

    private final SalonRepository salonRepository;

    @Transactional(readOnly = true)
    public List<SalonDto> getAll() {
        return salonRepository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SalonDto getById(UUID id) {
        return toDto(salonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "id", id)));
    }

    @Transactional(readOnly = true)
    public SalonDto getByName(String name) {
        return toDto(salonRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new ResourceNotFoundException("Salon", "name", name)));
    }

    @Transactional(readOnly = true)
    public List<SalonDto> getByCategory(String category) {
        return salonRepository.findByServiceCategory(category).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDto> getNearby(double lat, double lng, double radiusKm) {
        double dLat = radiusKm / 111.0;
        double dLng = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return salonRepository.findNearby(lat - dLat, lat + dLat, lng - dLng, lng + dLng)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<SalonDto> search(String query) {
        if (query == null || query.isBlank()) return getAll();
        return salonRepository.search(query.trim()).stream().map(this::toDto).toList();
    }

    // ---- mappers ----

    public SalonDto toDto(Salon salon) {
        return SalonDto.builder()
                .id(salon.getId().toString())
                .name(salon.getName())
                .description(salon.getDescription())
                .address(salon.getAddress())
                .phone(salon.getPhone())
                .imageUrl(salon.getImageData() != null && salon.getImageData().length > 0
                        ? "/api/salons/" + salon.getId() + "/image"
                        : salon.getImageUrl())
                .latitude(salon.getLatitude())
                .longitude(salon.getLongitude())
                .rating(salon.getRating())
                .reviewCount(salon.getReviewCount())
                .openingHours(salon.getOpeningHours())
                .tags(salon.getTags())
                .services(salon.getServices().stream().map(this::toServiceDto).toList())
                .build();
    }

    private ServiceDto toServiceDto(SalonService s) {
        return ServiceDto.builder()
                .id(s.getId().toString())
                .name(s.getName())
                .description(s.getDescription())
                .category(s.getCategory())
                .price(s.getPrice())
                .durationMinutes(s.getDurationMinutes())
                .icon(s.getIcon())
                .build();
    }
}
