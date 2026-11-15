package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HoldStateQuery {
    private final BookingRepository bookings;
    private final HoldPolicyStore store;
    public record HoldState(Long bookingId, Long driverId, String status, boolean unpaidReleaseCandidate) {}

    // Payment calls back while holding its attempt lock. This query must never lock booking rows.
    @Transactional(readOnly=true)
    public HoldState get(Long id) {
        var booking=bookings.findById(id).orElseThrow(()->new AppException(ErrorCode.BOOKING_NOT_FOUND,"Không tìm thấy vé"));
        return new HoldState(booking.getId(),booking.getDriverId(),booking.getStatus().name(),store.hasRelease(id));
    }
}
