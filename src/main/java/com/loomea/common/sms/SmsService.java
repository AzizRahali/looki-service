package com.loomea.common.sms;

/**
 * Sends SMS messages. Implementations decide the transport (Twilio, console log, etc.).
 */
public interface SmsService {

    /**
     * Send a text message.
     * @param to   full phone number including country code, e.g. "+1 5551234567"
     * @param body message text
     */
    void send(String to, String body);
}
