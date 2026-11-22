package com.smartparking.booking.domain;

import java.time.LocalDateTime;

public class HoldRestrictionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason { VEHICLE_LIMIT, ACCOUNT_LIMIT, COOLDOWN, RATE_LIMIT, PAYMENT_CHECK_PENDING }
    public record Details(Reason reason, LocalDateTime retryAt, Long existingBookingId) implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }
    private final Details details;
    public HoldRestrictionException(Reason reason, String message, LocalDateTime retryAt, Long existingBookingId) {
        super(message);
        this.details = new Details(reason, retryAt, existingBookingId);
    }
    public Details getDetails() { return details; }
    public String getCode() { return "BOOKING_HOLD_" + details.reason().name(); }
}
