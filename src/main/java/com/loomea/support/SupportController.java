package com.loomea.support;

import com.loomea.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "Support", description = "AI support assistant")
@SecurityRequirement(name = "Bearer JWT")
public class SupportController {

    private final SupportService supportService;

    @PostMapping("/chat")
    @Operation(summary = "Ask the AI support assistant; answers come only from the help articles")
    public ResponseEntity<ApiResponse<SupportChatResponse>> chat(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody SupportChatRequest request) {
        boolean owner = user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SALON_OWNER".equals(a.getAuthority()));
        return ResponseEntity.ok(ApiResponse.success(
                supportService.chat(UUID.fromString(user.getUsername()), owner, request)));
    }
}
