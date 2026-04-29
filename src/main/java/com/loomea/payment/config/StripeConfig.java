package com.loomea.payment.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.stripe")
@Getter
@Setter
public class StripeConfig {

    /** Stripe secret key (sk_test_... or sk_live_...) */
    private String secretKey;

    /**
     * Stripe webhook endpoint secret (whsec_...).
     * Leave blank during local dev — the status-polling endpoint verifies
     * with Stripe directly, so webhooks are optional.
     */
    private String webhookSecret;

    /**
     * URL Stripe redirects to after a successful payment.
     * The Flutter WebView intercepts this URL and closes automatically.
     * Default uses a placeholder host — it never needs to resolve as a real page.
     */
    private String successUrl = "https://loomea.app/payment/success";

    /**
     * URL Stripe redirects to when the user cancels on the Checkout page.
     * The Flutter WebView intercepts this URL and closes automatically.
     */
    private String cancelUrl = "https://loomea.app/payment/cancel";

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
