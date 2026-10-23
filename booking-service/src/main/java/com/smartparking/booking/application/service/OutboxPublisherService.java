package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.model.Booking;

public interface OutboxPublisherService {
    void publishBookingCreated(Booking booking);
    void publishBookingCheckedIn(Booking booking);
    void publishBookingCompleted(Booking booking);
    void publishBookingCancelled(Booking booking, String reason);
}
