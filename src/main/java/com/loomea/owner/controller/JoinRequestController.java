package com.loomea.owner.controller;

import com.loomea.common.dto.ApiResponse;
import com.loomea.owner.dto.CreateJoinRequestRequest;
import com.loomea.owner.dto.JoinRequestDto;
import com.loomea.owner.service.OwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/salons/{salonId}/join-request")
@RequiredArgsConstructor
@Tag(name = "Join Requests", description = "Request to join a salon as staff")
@SecurityRequirement(name = "Bearer JWT")
public class JoinRequestController {

    private final OwnerService ownerService;

    /**
     * POST /api/salons/{salonId}/join-request
     * Any authenticated user can submit a request to join a salon as staff.
     * The salon owner reviews it in their dashboard and approves or rejects.
     */
    @PostMapping
    @Operation(summary = "Submit a join request to a salon")
    public ResponseEntity<ApiResponse<JoinRequestDto>> submit(
            @PathVariable UUID salonId,
            @AuthenticationPrincipal UserDetails user,
            @RequestBody(required = false) CreateJoinRequestRequest req) {

        JoinRequestDto dto = ownerService.submitJoinRequest(
                salonId,
                UUID.fromString(user.getUsername()),
                req != null ? req : new CreateJoinRequestRequest());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Join request submitted", dto));
    }
}
