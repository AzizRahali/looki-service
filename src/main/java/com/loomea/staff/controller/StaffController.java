package com.loomea.staff.controller;

import com.loomea.booking.dto.BookingDto;
import com.loomea.common.dto.ApiResponse;
import com.loomea.staff.dto.StaffProfileDto;
import com.loomea.staff.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
@Tag(name = "Staff", description = "Staff member self-service endpoints")
@SecurityRequirement(name = "Bearer JWT")
public class StaffController {

    private final StaffService staffService;

    @GetMapping("/me")
    @Operation(summary = "Get staff member profile")
    public ResponseEntity<ApiResponse<StaffProfileDto>> getProfile(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(staffService.getProfile(userId)));
    }

    @GetMapping("/me/bookings/upcoming")
    @Operation(summary = "Get upcoming bookings assigned to this staff member")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getUpcoming(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(staffService.getUpcomingBookings(userId)));
    }

    @GetMapping("/me/bookings/past")
    @Operation(summary = "Get past bookings assigned to this staff member")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getPast(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(staffService.getPastBookings(userId)));
    }

    @GetMapping("/me/today")
    @Operation(summary = "Get today's bookings assigned to this staff member")
    public ResponseEntity<ApiResponse<List<BookingDto>>> getToday(
            @AuthenticationPrincipal UserDetails user) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success(staffService.getTodayBookings(userId)));
    }
}
