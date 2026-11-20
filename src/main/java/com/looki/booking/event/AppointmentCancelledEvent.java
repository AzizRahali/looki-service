package com.looki.booking.event;

import java.util.UUID;

/**
 * Published when an appointment is cancelled by the client or rejected by the
 * owner. The payment module listens for it to refund card payments.
 */
public record AppointmentCancelledEvent(UUID appointmentId) {
}
