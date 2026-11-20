package com.looki.payment.config;

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
     * ISO currency code every charge is made in. Must be one the Stripe
     * account supports — TND isn't, so app prices are converted (see below).
     */
    private String currency = "usd";

    /**
     * Charge-currency units per 1 unit of the app's prices (TND). Example:
     * currency "eur" with rate 0.29 charges 100 TND as 29.00 EUR. Leave at 1
     * to charge the app price as-is in {@link #currency}.
     */
    private double conversionRate = 1.0;

    /**
     * URL Stripe redirects to after a successful payment. Blank means
     * "{backend}/api/payments/return?result=success", built from the request,
     * which shows a small "return to the app" page. The in-app WebView
     * intercepts it before it loads.
     */
    private String successUrl;

    /** Same as {@link #successUrl}, for a cancelled Checkout. */
    private String cancelUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
    }
}
