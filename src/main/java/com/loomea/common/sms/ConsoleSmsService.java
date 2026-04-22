package com.loomea.common.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Fallback: logs the SMS to console when Twilio credentials are not configured.
 */
@Service
@ConditionalOnExpression("'${app.twilio.account-sid:}'.isBlank()")
@Slf4j
public class ConsoleSmsService implements SmsService {

    @Override
    public void send(String to, String body) {
        log.info("╔══════════════════════════════════════════");
        log.info("║  SMS (console)  →  {}", to);
        log.info("║  {}", body);
        log.info("╚══════════════════════════════════════════");
    }
}
