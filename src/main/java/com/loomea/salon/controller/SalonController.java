package com.loomea.salon.controller;

import com.loomea.common.dto.ApiResponse;
import com.loomea.owner.dto.StaffMemberDto;
import com.loomea.owner.repository.StaffMemberRepository;
import com.loomea.salon.dto.PortfolioPostDto;
import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.entity.Salon;
import com.loomea.salon.repository.PortfolioRepository;
import com.loomea.salon.repository.SalonRepository;
import com.loomea.salon.service.SalonServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/salons")
@RequiredArgsConstructor
@Tag(name = "Salons", description = "Salon listing and details")
public class SalonController {

    private final SalonServiceImpl salonService;
    private final PortfolioRepository portfolioRepository;
    private final SalonRepository salonRepository;
    private final StaffMemberRepository staffMemberRepository;

    @GetMapping
    @Operation(summary = "List all salons")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<SalonDto>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(salonService.getAll()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get salon by UUID")
    public ResponseEntity<ApiResponse<SalonDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(salonService.getById(id)));
    }

    @GetMapping("/by-name/{name}")
    @Operation(summary = "Get salon by name (case-insensitive)")
    public ResponseEntity<ApiResponse<SalonDto>> getByName(@PathVariable String name) {
        return ResponseEntity.ok(ApiResponse.success(salonService.getByName(name)));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get salons by service category")
    public ResponseEntity<ApiResponse<List<SalonDto>>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(salonService.getByCategory(category)));
    }

    @GetMapping("/nearby")
    @Operation(summary = "Get nearby salons")
    public ResponseEntity<ApiResponse<List<SalonDto>>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radius) {
        return ResponseEntity.ok(ApiResponse.success(salonService.getNearby(lat, lng, radius)));
    }

    @GetMapping("/{salonId}/image")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> getSalonImage(@PathVariable UUID salonId) {
        Salon salon = salonRepository.findById(salonId).orElse(null);
        if (salon == null || salon.getImageData() == null || salon.getImageData().length == 0) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(salon.getImageData());
    }

    @GetMapping("/{salonId}/portfolio")
    @Operation(summary = "Get portfolio posts for a salon")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<PortfolioPostDto>>> getPortfolio(@PathVariable UUID salonId) {
        List<PortfolioPostDto> posts = portfolioRepository.findBySalonIdOrderByCreatedAtDesc(salonId)
                .stream().map(p -> PortfolioPostDto.builder()
                        .id(p.getId().toString())
                        .salonId(p.getSalon().getId().toString())
                        .caption(p.getCaption())
                        .imageUrl("/api/portfolio/" + p.getId() + "/image")
                        .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "")
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/{salonId}/staff")
    @Operation(summary = "Get active staff members for a salon (public)")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<StaffMemberDto>>> getStaff(@PathVariable UUID salonId) {
        List<StaffMemberDto> staff = staffMemberRepository.findBySalonIdAndStatus(salonId, com.loomea.owner.entity.StaffStatus.ACTIVE)
                .stream().map(m -> StaffMemberDto.builder()
                        .id(m.getId().toString())
                        .salonId(salonId.toString())
                        .name(m.getName())
                        .role(m.getRole())
                        .staffRole(m.getStaffRole().name())
                        .status(m.getStatus().name())
                        .avatarUrl(m.getAvatarData() != null && m.getAvatarData().length > 0
                                ? "/api/owner/staff/" + m.getId() + "/avatar"
                                : null)
                        .build())
                .toList();
        return ResponseEntity.ok(ApiResponse.success(staff));
    }
}
