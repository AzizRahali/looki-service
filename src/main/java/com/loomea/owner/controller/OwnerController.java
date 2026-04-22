package com.loomea.owner.controller;

import com.loomea.booking.dto.BookingDto;
import com.loomea.common.dto.ApiResponse;
import com.loomea.owner.dto.*;
import com.loomea.owner.service.OwnerService;
import com.loomea.salon.dto.CreatePortfolioPostRequest;
import com.loomea.salon.dto.PortfolioPostDto;
import com.loomea.salon.dto.SalonDto;
import com.loomea.salon.dto.UpdatePortfolioPostRequest;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner")
@RequiredArgsConstructor
@Tag(name = "Owner", description = "Salon owner management — salon CRUD, services, bookings, dashboard")
@SecurityRequirement(name = "Bearer JWT")
public class OwnerController {

    private final OwnerService ownerService;

    // ──────────────── Salon CRUD ────────────────

    @PostMapping("/salons")
    @Operation(summary = "Create a new salon")
    public ResponseEntity<ApiResponse<SalonDto>> createSalon(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreateSalonRequest request) {
        UUID ownerId = UUID.fromString(user.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Salon created", ownerService.createSalon(ownerId, request)));
    }

    @GetMapping("/salons")
    @Operation(summary = "List my salons")
    public ResponseEntity<ApiResponse<List<SalonDto>>> mySalons(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getMySalons(UUID.fromString(user.getUsername()))));
    }

    @GetMapping("/salons/{salonId}")
    @Operation(summary = "Get one of my salons by ID")
    public ResponseEntity<ApiResponse<SalonDto>> mySalon(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getMySalon(salonId, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/salons/{salonId}")
    @Operation(summary = "Update my salon")
    public ResponseEntity<ApiResponse<SalonDto>> updateSalon(
            @PathVariable UUID salonId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody UpdateSalonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Salon updated",
                ownerService.updateSalon(salonId, UUID.fromString(user.getUsername()), request)));
    }

    @PostMapping("/salons/{salonId}/image")
    @Operation(summary = "Upload or replace the salon cover image (Base64 JPEG/PNG)")
    public ResponseEntity<ApiResponse<SalonDto>> uploadSalonImage(
            @PathVariable UUID salonId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(ApiResponse.success("Image updated",
                ownerService.uploadSalonImage(salonId, UUID.fromString(user.getUsername()), body.get("imageData"))));
    }

    @DeleteMapping("/salons/{salonId}")
    @Operation(summary = "Delete my salon")
    public ResponseEntity<ApiResponse<Void>> deleteSalon(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        ownerService.deleteSalon(salonId, UUID.fromString(user.getUsername()));
        return ResponseEntity.ok(ApiResponse.success("Salon deleted", null));
    }

    // ──────────────── Service CRUD ────────────────

    @PostMapping("/salons/{salonId}/services")
    @Operation(summary = "Add a service to my salon")
    public ResponseEntity<ApiResponse<SalonDto>> addService(
            @PathVariable UUID salonId,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreateServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Service added",
                        ownerService.addService(salonId, UUID.fromString(user.getUsername()), request)));
    }

    @PutMapping("/salons/{salonId}/services/{serviceId}")
    @Operation(summary = "Update a service in my salon")
    public ResponseEntity<ApiResponse<SalonDto>> updateService(
            @PathVariable UUID salonId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Service updated",
                ownerService.updateService(salonId, serviceId, UUID.fromString(user.getUsername()), request)));
    }

    @DeleteMapping("/salons/{salonId}/services/{serviceId}")
    @Operation(summary = "Remove a service from my salon")
    public ResponseEntity<ApiResponse<SalonDto>> removeService(
            @PathVariable UUID salonId,
            @PathVariable UUID serviceId,
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Service removed",
                ownerService.removeService(salonId, serviceId, UUID.fromString(user.getUsername()))));
    }

    // ──────────────── Bookings ────────────────

    @GetMapping("/salons/{salonId}/bookings")
    @Operation(summary = "All bookings for my salon")
    public ResponseEntity<ApiResponse<List<BookingDto>>> allBookings(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getAllBookings(salonId, UUID.fromString(user.getUsername()))));
    }

    @GetMapping("/salons/{salonId}/bookings/upcoming")
    @Operation(summary = "Upcoming bookings for my salon")
    public ResponseEntity<ApiResponse<List<BookingDto>>> upcomingBookings(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getUpcomingBookings(salonId, UUID.fromString(user.getUsername()))));
    }

    @GetMapping("/salons/{salonId}/bookings/past")
    @Operation(summary = "Past bookings for my salon")
    public ResponseEntity<ApiResponse<List<BookingDto>>> pastBookings(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getPastBookings(salonId, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/bookings/{bookingId}/confirm")
    @Operation(summary = "Confirm a pending booking")
    public ResponseEntity<ApiResponse<BookingDto>> confirmBooking(
            @PathVariable UUID bookingId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Booking confirmed",
                ownerService.confirmBooking(bookingId, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/bookings/{bookingId}/complete")
    @Operation(summary = "Mark a booking as completed")
    public ResponseEntity<ApiResponse<BookingDto>> completeBooking(
            @PathVariable UUID bookingId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Booking completed",
                ownerService.completeBooking(bookingId, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/bookings/{bookingId}/reject")
    @Operation(summary = "Reject (cancel) a booking")
    public ResponseEntity<ApiResponse<BookingDto>> rejectBooking(
            @PathVariable UUID bookingId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Booking rejected",
                ownerService.rejectBooking(bookingId, UUID.fromString(user.getUsername()))));
    }

    @PutMapping("/bookings/{bookingId}/no-show")
    @Operation(summary = "Mark a booking as no-show — applies -20 reliability penalty to the client")
    public ResponseEntity<ApiResponse<BookingDto>> markNoShow(
            @PathVariable UUID bookingId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success("Booking marked as no-show",
                ownerService.markNoShow(bookingId, UUID.fromString(user.getUsername()))));
    }

    // ──────────────── Portfolio ────────────────

    @GetMapping("/salons/{salonId}/portfolio")
    @Operation(summary = "Get portfolio posts for my salon")
    public ResponseEntity<ApiResponse<List<PortfolioPostDto>>> getPortfolio(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getPortfolioPosts(salonId, UUID.fromString(user.getUsername()))));
    }

    @PostMapping("/salons/{salonId}/portfolio")
    @Operation(summary = "Add a portfolio post")
    public ResponseEntity<ApiResponse<PortfolioPostDto>> addPortfolioPost(
            @PathVariable UUID salonId,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody CreatePortfolioPostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Post added",
                        ownerService.addPortfolioPost(salonId, UUID.fromString(user.getUsername()), request)));
    }

    @PutMapping("/salons/{salonId}/portfolio/{postId}")
    @Operation(summary = "Update a portfolio post caption or image")
    public ResponseEntity<ApiResponse<PortfolioPostDto>> updatePortfolioPost(
            @PathVariable UUID salonId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody UpdatePortfolioPostRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Post updated",
                ownerService.updatePortfolioPost(salonId, postId, UUID.fromString(user.getUsername()), request)));
    }

    @DeleteMapping("/salons/{salonId}/portfolio/{postId}")
    @Operation(summary = "Delete a portfolio post")
    public ResponseEntity<ApiResponse<Void>> deletePortfolioPost(
            @PathVariable UUID salonId,
            @PathVariable UUID postId,
            @AuthenticationPrincipal UserDetails user) {
        ownerService.deletePortfolioPost(salonId, postId, UUID.fromString(user.getUsername()));
        return ResponseEntity.ok(ApiResponse.success("Post deleted", null));
    }

    // ──────────────── Dashboard ────────────────

    @GetMapping("/salons/{salonId}/dashboard")
    @Operation(summary = "Get salon dashboard with stats")
    public ResponseEntity<ApiResponse<DashboardDto>> dashboard(
            @PathVariable UUID salonId, @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                ownerService.getDashboard(salonId, UUID.fromString(user.getUsername()))));
    }
}
