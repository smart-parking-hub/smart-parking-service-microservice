package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.CreateReviewRequest;
import com.smartparking.booking.application.dto.ReviewDto;
import com.smartparking.booking.application.service.impl.ReviewServiceImpl;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.model.Review;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.domain.repository.ReviewRepository;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private final Long driverUserId = 100L;
    private final Long bookingId = 55L;

    @Test
    @DisplayName("Create review fails when user is not the booking driver")
    void testCreateReviewForbidden() {
        Booking booking = Booking.builder().id(bookingId).driverId(999L).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CreateReviewRequest req = CreateReviewRequest.builder().bookingId(bookingId).rating(5).build();

        AppException ex = assertThrows(AppException.class, () -> reviewService.createReview(driverUserId, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create review fails when booking is not in COMPLETED status")
    void testCreateReviewInvalidStatus() {
        Booking booking = Booking.builder().id(bookingId).driverId(driverUserId).status(BookingStatusEnum.PARKING).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        CreateReviewRequest req = CreateReviewRequest.builder().bookingId(bookingId).rating(5).build();

        AppException ex = assertThrows(AppException.class, () -> reviewService.createReview(driverUserId, req));
        assertEquals(ErrorCode.BOOKING_STATUS_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create review fails when booking has already been reviewed")
    void testCreateReviewConflict() {
        Booking booking = Booking.builder().id(bookingId).driverId(driverUserId).status(BookingStatusEnum.COMPLETED).build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(true);

        CreateReviewRequest req = CreateReviewRequest.builder().bookingId(bookingId).rating(5).build();

        AppException ex = assertThrows(AppException.class, () -> reviewService.createReview(driverUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create review successfully persists review")
    void testCreateReviewSuccess() {
        Booking booking = Booking.builder()
                .id(bookingId)
                .parkingLotId(10L)
                .driverId(driverUserId)
                .status(BookingStatusEnum.COMPLETED)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingId(bookingId)).thenReturn(false);

        Review saved = Review.builder()
                .id(1L)
                .booking(booking)
                .parkingLotId(10L)
                .driverId(driverUserId)
                .rating(5)
                .comment("Bãi xe rất tốt")
                .build();
        when(reviewRepository.save(any(Review.class))).thenReturn(saved);

        CreateReviewRequest req = CreateReviewRequest.builder()
                .bookingId(bookingId)
                .rating(5)
                .comment("Bãi xe rất tốt")
                .build();

        ReviewDto dto = reviewService.createReview(driverUserId, req);

        assertNotNull(dto);
        assertEquals(5, dto.getRating());
        assertEquals("Bãi xe rất tốt", dto.getComment());
    }
}
