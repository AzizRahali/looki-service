package com.loomea.common.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(SmtpEmailService.class)
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

    @Override
    public void sendOtp(String toEmail, String recipientName, String otp) {
        log.info("""
                ╔══════════════════════════════════════════╗
                  VERIFICATION OTP (dev console)
                  To:      {}
                  Name:    {}
                  Code:    {}
                ╚══════════════════════════════════════════╝
                """, toEmail, recipientName, otp);
    }
}
