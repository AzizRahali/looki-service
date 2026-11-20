package com.looki.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KonnectInitRequest {

    private String receiverWalletId;
    private Long amount;          // millimes (TND×1000) or centimes (EUR×100)
    private String token;         // "TND" | "EUR" | "USD"
    private String type;          // "immediate"
    private String description;
    private List<String> acceptedPaymentMethods;
    private Integer lifespan;     // minutes until link expires
    private String orderId;       // our reference (appointmentId or userId:PLAN)
    private String webhook;       // our webhook URL
    private String firstName;
    private String lastName;
    private String email;
}
