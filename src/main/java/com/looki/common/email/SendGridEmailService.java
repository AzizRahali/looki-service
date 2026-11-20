package com.looki.common.email;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnExpression("!'${app.sendgrid.api-key:}'.isBlank()")
@Slf4j
public class SendGridEmailService implements EmailService {

    private final SendGrid sendGrid;
    private final String fromEmail;

    public SendGridEmailService(
            @Value("${app.sendgrid.api-key}") String apiKey,
            @Value("${app.sendgrid.from-email}") String fromEmail) {
        this.sendGrid = new SendGrid(apiKey);
        this.fromEmail = fromEmail;
    }

    // ── Inline SVG logo (matches LookiLogo CustomPaint in Flutter) ───────────
    private static final String LOGO_SVG = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="-100 -140 200 240" width="56" height="67">
          <!-- Lashes -->
          <g fill="none" stroke="#0A2A6B" stroke-width="4" stroke-linecap="round">
            <path d="M-60,-92 Q-66,-108 -72,-118"/>
            <path d="M-30,-98 Q-32,-116 -34,-128"/>
            <path d="M0,-100 Q0,-118 0,-130"/>
            <path d="M30,-98 Q32,-116 34,-128"/>
            <path d="M60,-92 Q66,-108 72,-118"/>
          </g>
          <!-- Navy squircle -->
          <path d="M-90,0 C-90,-63 -63,-90 0,-90 C63,-90 90,-63 90,0 C90,63 63,90 0,90 C-63,90 -90,63 -90,0Z" fill="#0A2A6B"/>
          <!-- Blue ring -->
          <path d="M-68,0 C-68,-47 -47,-68 0,-68 C47,-68 68,-47 68,0 C68,47 47,68 0,68 C-47,68 -68,47 -68,0Z" fill="#1E6FE8"/>
          <!-- White sclera -->
          <circle cx="0" cy="0" r="42" fill="#fff"/>
          <!-- Pupil -->
          <circle cx="11" cy="-11" r="22" fill="#0A2A6B"/>
          <!-- Catch-light -->
          <circle cx="4" cy="-18" r="6.5" fill="#fff"/>
        </svg>
        """;

    // ── Shared email wrapper ─────────────────────────────────────────────────
    private static String wrapEmail(String bodyContent) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="margin:0;padding:0;background-color:#FAF7F2;font-family:'Helvetica Neue',Helvetica,Arial,sans-serif">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#FAF7F2">
                <tr><td align="center" style="padding:40px 16px">
                  <table role="presentation" width="480" cellpadding="0" cellspacing="0"
                         style="max-width:480px;width:100%%;background-color:#FFFFFF;border-radius:16px;border:1px solid #E7E3DA;overflow:hidden">
                    <!-- Logo header -->
                    <tr>
                      <td align="center" style="padding:32px 32px 0 32px">
                        %s
                        <p style="margin:12px 0 0 0;font-size:18px;font-weight:700;letter-spacing:-0.5px;color:#0A2A6B">looki</p>
                      </td>
                    </tr>
                    <!-- Divider -->
                    <tr>
                      <td style="padding:20px 32px 0 32px">
                        <hr style="border:none;border-top:1px solid #E7E3DA;margin:0">
                      </td>
                    </tr>
                    <!-- Body -->
                    <tr>
                      <td style="padding:24px 32px 32px 32px">
                        %s
                      </td>
                    </tr>
                    <!-- Footer -->
                    <tr>
                      <td style="padding:0 32px 24px 32px">
                        <hr style="border:none;border-top:1px solid #E7E3DA;margin:0 0 16px 0">
                        <p style="margin:0;font-size:12px;color:#8A92A8;text-align:center;line-height:1.5">
                          &copy; %d Looki &middot; Salon Booking Platform
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
            """.formatted(LOGO_SVG, bodyContent, java.time.Year.now().getValue());
    }

    @Override
    public void sendPasswordResetLink(String toEmail, String recipientName, String resetLink) {
        String body = """
            <h2 style="margin:0 0 8px 0;font-size:22px;font-weight:700;color:#0A2A6B;letter-spacing:-0.3px">
              Reset your password
            </h2>
            <p style="margin:0 0 20px 0;font-size:15px;color:#8A92A8;line-height:1.5">
              Hi %s, we received a request to reset your password. Click the button below to choose a new one. This link expires in <strong style="color:#0A2A6B">15 minutes</strong>.
            </p>
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 auto 24px auto">
              <tr><td align="center" style="background-color:#0A2A6B;border-radius:12px">
                <a href="%s"
                   style="display:inline-block;padding:14px 32px;font-size:15px;font-weight:600;
                          color:#FFFFFF;text-decoration:none;letter-spacing:0.2px">
                  Reset Password
                </a>
              </td></tr>
            </table>
            <p style="margin:0 0 8px 0;font-size:13px;color:#8A92A8;line-height:1.5">
              If the button doesn't work, copy and paste this link into your browser:
            </p>
            <p style="margin:0 0 20px 0;font-size:13px;word-break:break-all">
              <a href="%s" style="color:#1E6FE8;text-decoration:underline">%s</a>
            </p>
            <p style="margin:0;font-size:13px;color:#8A92A8;line-height:1.5">
              If you didn't request this, you can safely ignore this email.
            </p>
            """.formatted(recipientName, resetLink, resetLink, resetLink);

        send(toEmail, "Reset your Looki password", wrapEmail(body));
    }

    @Override
    public void sendOtp(String toEmail, String recipientName, String otp) {
        String body = """
            <h2 style="margin:0 0 8px 0;font-size:22px;font-weight:700;color:#0A2A6B;letter-spacing:-0.3px">
              Verify your email
            </h2>
            <p style="margin:0 0 24px 0;font-size:15px;color:#8A92A8;line-height:1.5">
              Hi %s, use the code below to verify your Looki account.
            </p>
            <table role="presentation" cellpadding="0" cellspacing="0" width="100%%"
                   style="margin:0 0 24px 0">
              <tr><td align="center"
                      style="background-color:#FAF7F2;border:1px solid #E7E3DA;border-radius:12px;padding:20px 0">
                <span style="font-size:36px;font-weight:700;letter-spacing:10px;color:#0A2A6B">%s</span>
              </td></tr>
            </table>
            <p style="margin:0 0 4px 0;font-size:14px;color:#0A2A6B;font-weight:600">
              This code expires in 10 minutes.
            </p>
            <p style="margin:0;font-size:13px;color:#8A92A8;line-height:1.5">
              If you didn't create a Looki account, you can safely ignore this email.
            </p>
            """.formatted(recipientName, otp);

        send(toEmail, "Your Looki verification code: " + otp, wrapEmail(body));
    }

    private void send(String toEmail, String subject, String html) {
        Email from = new Email(fromEmail, "Looki");
        Email to = new Email(toEmail);
        Content content = new Content("text/html", html);
        Mail mail = new Mail(from, subject, to, content);

        try {
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                log.info("Email sent to {} (status {})", toEmail, response.getStatusCode());
            } else {
                log.error("SendGrid error {}: {}", response.getStatusCode(), response.getBody());
                throw new RuntimeException("SendGrid returned " + response.getStatusCode());
            }
        } catch (IOException e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send verification email", e);
        }
    }
}
