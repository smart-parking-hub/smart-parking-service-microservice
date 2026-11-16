package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.CheckOutResponse;

import java.math.BigDecimal;

public interface CheckInOutService {
    void checkIn(Long merchantUserId, String bookingCode, Long parkingLotId);
    CheckOutResponse checkOut(Long merchantUserId, String bookingCode, Long parkingLotId);
    void confirmCashPayment(Long merchantUserId, String bookingCode, Long parkingLotId, BigDecimal amount);
    default void confirmCashPayment(Long merchantUserId, String bookingCode, Long parkingLotId) {
        confirmCashPayment(merchantUserId, bookingCode, parkingLotId, null);
    }
}
