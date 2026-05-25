package com.loomea.common.email;


import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.mail.host", matchIfMissing = false)
@Slf4j
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromEmail;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String fromEmail) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
    }

    @Override
    public void sendPasswordResetLink(String toEmail, String recipientName, String resetLink) {
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                  <h2>Reset your password</h2>
                  <p>Hi %s,</p>
                  <p>Click the link below to reset your Loomea password. It expires in 15 minutes.</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 24px;background:#1a1a2e;color:#fff;
                     border-radius:8px;text-decoration:none;font-weight:600">Reset Password</a></p>
                  <p style="color:#888;font-size:13px">If you didn't request this, you can safely ignore this email.</p>
                </div>
                """.formatted(recipientName, resetLink);
        send(toEmail, "Reset your Loomea password", html);
    }

    @Override
    public void sendOtp(String toEmail, String recipientName, String otp) {
        String html = """
                <div style="font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px">
                  <h2>Verify your email</h2>
                  <p>Hi %s,</p>
                  <p>Your Loomea verification code is:</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:8px;text-align:center;
                     padding:16px 0;color:#1a1a2e">%s</p>
                  <p>This code expires in 10 minutes.</p>
                  <p style="color:#888;font-size:13px">If you didn't create a Loomea account, ignore this email.</p>
                </div>
                """.formatted(recipientName, otp);
        send(toEmail, "Your Loomea verification code: " + otp, html);
    }

    private void send(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email sent to {}", toEmail);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}
