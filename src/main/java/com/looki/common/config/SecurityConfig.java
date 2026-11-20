package com.looki.common.config;

import com.looki.common.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Public: no token required ──────────────────────
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/request-otp").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/verify-email").permitAll()
                        // Password reset — must be public (user has no token yet)
                        .requestMatchers(HttpMethod.POST, "/api/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/auth/validate-reset-token").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/reset-password").permitAll()
                        // User avatars — public for booking cards & reviews
                        .requestMatchers(HttpMethod.GET, "/api/auth/users/*/avatar").permitAll()
                        // Salons & search — browse without account
                        .requestMatchers(HttpMethod.GET, "/api/salons/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                        // Service catalog — public so clients & owners can browse
                        .requestMatchers(HttpMethod.GET, "/api/services/catalog").permitAll()
                        // Slot availability — public so guest can check before signing up
                        .requestMatchers(HttpMethod.GET, "/api/bookings/slots").permitAll()
                        // Reviews — reading is public
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                        // Portfolio images — public so clients can view without auth
                        .requestMatchers(HttpMethod.GET, "/api/portfolio/**").permitAll()
                        // H2 console (dev only)
                        .requestMatchers("/h2-console/**").permitAll()
                        // Stripe webhook — public, signature verified inside service
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()
                        // Stripe Checkout return page (opened in the browser after paying)
                        .requestMatchers(HttpMethod.GET, "/api/payments/return").permitAll()
                        // ── Subscriptions — authenticated SALON_OWNER only ──
                        .requestMatchers("/api/subscriptions/**").hasRole("SALON_OWNER")
                        // ── Role-restricted ────────────────────────────────
                        .requestMatchers("/api/owner/**").hasRole("SALON_OWNER")
                        // ── Everything else requires a valid JWT ───────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers(
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/swagger-resources/**",
                "/api-docs/**",
                "/api-docs",
                "/v3/api-docs/**",
                "/v3/api-docs",
                "/webjars/**"
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
