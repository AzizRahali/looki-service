package com.looki.payment.dto;

import lombok.Data;

@Data
public class KonnectInitResponse {
    private String payUrl;
    private String paymentRef;
}
