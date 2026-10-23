package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.dto.CreateReviewRequest;
import com.smartparking.booking.application.dto.ReviewDto;
import com.smartparking.booking.application.service.ReviewService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateReviewRequest request) {
        ReviewDto dto = reviewService.createReview(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Đánh giá thành công!"));
    }

    @GetMapping({"/parking-lot/{parkingLotId}", "/parking-lots/{parkingLotId}"})
    public ResponseEntity<ApiResponse<PageResponse<ReviewDto>>> getReviews(
            @PathVariable Long parkingLotId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<ReviewDto> page = reviewService.getReviews(parkingLotId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Lấy danh sách đánh giá thành công"));
    }
}
