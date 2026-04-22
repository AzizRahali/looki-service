package com.loomea.common.sms;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

/**
 * Sends real SMS via Twilio.
 * Activated only when {@code app.twilio.account-sid} is a non-empty string.
 */
@Service
@ConditionalOnExpression("!'${app.twilio.account-sid:}'.isBlank()")
@Slf4j
public class TwilioSmsService implements SmsService {

    @Value("${app.twilio.account-sid}")
    private String accountSid;

    @Value("${app.twilio.auth-token}")
    private String authToken;

    @Value("${app.twilio.phone-number}")
    private String fromNumber;

    @PostConstruct
    void init() {
        Twilio.init(accountSid, authToken);
        log.info("Twilio SMS service initialized (from: {})", fromNumber);
    }

    @Override
    public void send(String to, String body) {
        // Strip spaces from the phone number for Twilio
        String cleaned = to.replaceAll("\\s+", "");
        Message msg = Message.creator(
                new PhoneNumber(cleaned),
                new PhoneNumber(fromNumber),
                body
        ).create();
        log.info("SMS sent to {} — SID: {}", cleaned, msg.getSid());
    }
}
