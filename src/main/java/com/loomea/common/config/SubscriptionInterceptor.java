package com.loomea.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomea.subscription.service.SubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Interceptor that blocks access to all /api/owner/** endpoints unless
 * the authenticated salon owner has an ACTIVE subscription.
 */
@Component
@RequiredArgsConstructor

public class SubscriptionInterceptor implements HandlerInterceptor {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return true; // let security filter handle it

        Object principal = auth.getPrincipal();
        if (!(principal instanceof UserDetails userDetails)) return true;

        UUID userId = UUID.fromString(userDetails.getUsername());
        if (!subscriptionService.hasActiveSubscription(userId)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "SUBSCRIPTION_REQUIRED: An active subscription is needed to access owner features.",
                    "data", null
            ));
            return false;
        }

        return true;
    }
}
