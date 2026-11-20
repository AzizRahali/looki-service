package com.looki.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.looki.salon.repository.SalonRepository;
import com.looki.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subscription gate: once an owner has created their first salon,
 * all /api/owner/** requests require an ACTIVE subscription.
 * During onboarding (no salon yet) the interceptor is transparent.
 */
@Component
@RequiredArgsConstructor
public class SubscriptionInterceptor implements HandlerInterceptor {

    private final SubscriptionService subscriptionService;
    private final SalonRepository salonRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true;

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetails userDetails)) return true;

        UUID userId = UUID.fromString(userDetails.getUsername());

        // Owner hasn't created a salon yet — still in onboarding, no subscription required
        if (!salonRepository.existsByOwnerId(userId)) return true;

        // Owner has at least one salon — subscription is now required
        if (!subscriptionService.hasActiveSubscription(userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Map<String, Object> body = new HashMap<>();
            body.put("success", false);
            body.put("message", "SUBSCRIPTION_REQUIRED: An active subscription is needed to access owner features.");
            objectMapper.writeValue(response.getWriter(), body);
            return false;
        }

        return true;
    }
}
