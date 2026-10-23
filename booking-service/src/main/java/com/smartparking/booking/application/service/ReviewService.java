package com.smartparking.booking.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.booking.application.dto.CreateReviewRequest;
import com.smartparking.booking.application.dto.ReviewDto;
import org.springframework.data.domain.Pageable;

public interface ReviewService {
    ReviewDto createReview(Long driverUserId, CreateReviewRequest request);
    PageResponse<ReviewDto> getReviews(Long parkingLotId, Pageable pageable);
}
