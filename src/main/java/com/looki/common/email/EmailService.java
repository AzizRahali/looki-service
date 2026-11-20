package com.looki.common.email;

public interface EmailService {
    void sendPasswordResetLink(String toEmail, String recipientName, String resetLink);
    void sendOtp(String toEmail, String recipientName, String otp);
}
