package com.loomea.common.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Calls endpoints of the Modal-hosted Looki AI model. Shares one token, timeout
 * and rate limit across all AI features. Any failure yields an empty result so
 * callers can fall back to non-AI behaviour.
 */
@Slf4j
@Component
public class ModalClient {

    private final String token;
    private final int maxRequestsPerMinute;
    private final RestClient restClient;

    private long windowStartMillis;
    private int requestsInWindow;

    public ModalClient(
            @Value("${app.ai.modal-token:}") String token,
            @Value("${app.ai.timeout-seconds:90}") long timeoutSeconds,
            @Value("${app.ai.max-requests-per-minute:20}") int maxRequestsPerMinute) {
        this.token = token;
        this.maxRequestsPerMinute = maxRequestsPerMinute;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // Generous timeout: the first request after idling waits for a GPU cold start.
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public boolean isEnabled(String url) {
        return url != null && !url.isBlank() && !token.isBlank();
    }

    public <T> Optional<T> post(String url, Map<String, Object> payload, Class<T> responseType) {
        if (!isEnabled(url)) {
            return Optional.empty();
        }
        if (!tryAcquire()) {
            log.warn("AI rate limit reached; using fallback");
            return Optional.empty();
        }
        Map<String, Object> body = new HashMap<>(payload);
        body.put("token", token);
        try {
            return Optional.ofNullable(restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(responseType));
        } catch (RestClientException e) {
            log.warn("AI unavailable ({}); using fallback", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private synchronized boolean tryAcquire() {
        long now = System.currentTimeMillis();
        if (now - windowStartMillis >= 60_000) {
            windowStartMillis = now;
            requestsInWindow = 0;
        }
        if (requestsInWindow >= maxRequestsPerMinute) {
            return false;
        }
        requestsInWindow++;
        return true;
    }
}
