package com.loomea.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Development email service — logs the reset link to console instead of sending.
 * Replace with a real SendGrid / SES implementation for production.
 */
@Service
@Slf4j
public class ConsoleEmailService implements EmailService {

    @Override
    public void sendPasswordResetLink(String toEmail, String recipientName, String resetLink) {
        log.info("""
                ╔══════════════════════════════════════════╗
                  PASSWORD RESET EMAIL (dev console)
                  To:      {}
                  Name:    {}
                  Link:    {}
                ╚══════════════════════════════════════════╝
                """, toEmail, recipientName, resetLink);
    }
}
