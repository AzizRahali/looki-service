package com.loomea.common.email;

/**
 * Sends transactional emails. Swap implementations via Spring profile:
 *  - "dev"  → ConsoleEmailService (logs to stdout)
 *  - "prod" → SendGridEmailService / SesEmailService (plug in your provider)
 */
public interface EmailService {
    void sendPasswordResetLink(String toEmail, String recipientName, String resetLink);
}
