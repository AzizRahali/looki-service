package com.looki.payment.service;

import java.util.Locale;
import java.util.Set;

/**
 * Converts between major units (29.99) and Stripe's smallest-unit integers.
 * See https://docs.stripe.com/currencies#minor-units
 */
final class CurrencyAmounts {

    private static final Set<String> ZERO_DECIMAL = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga",
            "pyg", "rwf", "ugx", "vnd", "vuv", "xaf", "xof", "xpf");

    /** Stripe requires three-decimal amounts to end in 0. */
    private static final Set<String> THREE_DECIMAL = Set.of("bhd", "jod", "kwd", "omr", "tnd");

    private CurrencyAmounts() {}

    static long toMinorUnits(double amount, String currency) {
        String c = currency.toLowerCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(c)) return Math.round(amount);
        if (THREE_DECIMAL.contains(c)) return Math.round(amount * 100) * 10;
        return Math.round(amount * 100);
    }

    static double toMajorUnits(long minor, String currency) {
        String c = currency.toLowerCase(Locale.ROOT);
        if (ZERO_DECIMAL.contains(c)) return minor;
        if (THREE_DECIMAL.contains(c)) return minor / 1000.0;
        return minor / 100.0;
    }
}
