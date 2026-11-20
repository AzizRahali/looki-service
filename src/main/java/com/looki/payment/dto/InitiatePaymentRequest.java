package com.looki.payment.dto;

import lombok.Data;

import java.util.List;

@Data
public class InitiatePaymentRequest {

    /** "BOOKING" or "SUBSCRIPTION" */
    private String type;

    // ── BOOKING fields ─────────────────────────────────────────────────────────
    private String salonId;
    private String date;         // ISO: yyyy-MM-dd
    private String startTime;    // HH:mm (24-hour)
    private List<String> serviceIds;
    private String notes;

    // ── SUBSCRIPTION fields ────────────────────────────────────────────────────
    /** "MONTHLY" or "YEARLY" */
    private String plan;
}
