package com.loomea.booking.controller;

import com.loomea.booking.dto.WaitlistDto;
import com.loomea.booking.service.WaitlistService;
import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/waitlist")
@RequiredArgsConstructor
@Tag(name = "Waitlist", description = "Waitlist for fully-booked dates")
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    @Operation(summary = "Join waitlist", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<WaitlistDto>> join(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> body) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        UUID salonId = UUID.fromString(body.get("salonId"));
        LocalDate date = LocalDate.parse(body.get("date"));
        return ResponseEntity.ok(ApiResponse.success("Added to waitlist",
                waitlistService.join(userId, salonId, date)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Leave waitlist", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<Void>> leave(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID id) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        waitlistService.leave(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Removed from waitlist", null));
    }

    @GetMapping("/my")
    @Operation(summary = "My waitlist entries", security = @SecurityRequirement(name = "Bearer JWT"))
    public ResponseEntity<ApiResponse<List<WaitlistDto>>> myEntries(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(waitlistService.getMyAll(userId)));
    }
}
