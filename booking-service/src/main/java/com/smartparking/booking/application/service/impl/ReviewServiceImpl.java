package com.smartparking.booking.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.booking.application.dto.CreateReviewRequest;
import com.smartparking.booking.application.dto.ReviewDto;
import com.smartparking.booking.application.service.ReviewService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.model.Review;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public ReviewDto createReview(Long driverUserId, CreateReviewRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));

        if (!booking.getDriverId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không thể đánh giá booking của người khác");
        }

        if (booking.getStatus() != BookingStatusEnum.COMPLETED) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Chỉ có thể đánh giá lượt đỗ xe đã hoàn thành");
        }

        if (reviewRepository.existsByBookingId(request.getBookingId())) {
            throw new AppException(ErrorCode.CONFLICT, "Lượt đỗ xe này đã được đánh giá trước đó");
        }

        Review review = Review.builder()
                .booking(booking)
                .parkingLotId(booking.getParkingLotId())
                .driverId(driverUserId)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Driver {} reviewed lot {} with rating {}", driverUserId, booking.getParkingLotId(), request.getRating());
        return ReviewDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getReviews(Long parkingLotId, Pageable pageable) {
        Page<ReviewDto> page = reviewRepository.findByParkingLotIdOrderByCreatedAtDesc(parkingLotId, pageable)
                .map(ReviewDto::fromEntity);
        return new PageResponse<>(page);
    }
}
