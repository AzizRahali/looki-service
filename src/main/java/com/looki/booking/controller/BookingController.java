package com.looki.booking.controller;

import com.looki.booking.dto.*;
import com.looki.booking.service.BookingService;
import com.looki.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Bookings", description = "Book, reschedule and cancel appointments")
@SecurityRequirement(name = "Bearer JWT")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a booking")
    public ResponseEntity<ApiResponse<BookingDto>> create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreateBookingRequest request) {
        UUID userId = UUID.fromString(user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Booking confirmed", bookingService.create(userId, request)));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get upcoming appointments")
    public ResponseEntity<ApiResponse<List<BookingDto>>> upcoming(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getUpcoming(UUID.fromString(user.getUsername()))));
    }

    @GetMapping("/past")
    @Operation(summary = "Get past appointments")
    public ResponseEntity<ApiResponse<List<BookingDto>>> past(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getPast(UUID.fromString(user.getUsername()))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<ApiResponse<BookingDto>> getById(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                bookingService.getById(id, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel an appointment")
    public ResponseEntity<ApiResponse<BookingDto>> cancel(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Appointment cancelled",
                bookingService.cancel(id, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Reschedule an appointment")
    public ResponseEntity<ApiResponse<BookingDto>> reschedule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody RescheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Appointment rescheduled",
                bookingService.reschedule(id, UUID.fromString(user.getUsername()), request)));
    }

    @GetMapping("/slots")
    @Operation(summary = "Get available time slots")
    public ResponseEntity<ApiResponse<List<TimeSlotDto>>> slots(
            @RequestParam UUID salonId,
            @RequestParam LocalDate date,
            @RequestParam(defaultValue = "60") int duration) {
        return ResponseEntity.ok(ApiResponse.success(bookingService.getSlots(salonId, date, duration)));
    }

    @PutMapping("/{id}/confirm-attendance")
    @Operation(summary = "Confirm attendance for a risky-tier appointment")
    public ResponseEntity<ApiResponse<BookingDto>> confirmAttendance(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Attendance confirmed",
                bookingService.confirmAttendance(id, UUID.fromString(user.getUsername()))));
    }
}
