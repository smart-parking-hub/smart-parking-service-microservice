package com.smartparking.booking.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.application.dto.CancelBookingResponse;
import com.smartparking.booking.application.dto.CreateBookingRequest;
import org.springframework.data.domain.Pageable;

public interface BookingService {
    BookingDto createBooking(Long driverUserId, CreateBookingRequest request);
    PageResponse<BookingDto> getMyBookings(Long driverUserId, Pageable pageable);
    PageResponse<BookingDto> getMerchantBookings(Long merchantUserId, Pageable pageable);
    BookingDto getBookingDetail(Long userId, Long bookingId);
    CancelBookingResponse cancelBooking(Long driverUserId, Long bookingId, CancelBookingRequest request);
}
