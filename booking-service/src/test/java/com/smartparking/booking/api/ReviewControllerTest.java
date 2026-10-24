package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.ReviewController;
import com.smartparking.booking.application.dto.CreateReviewRequest;
import com.smartparking.booking.application.dto.ReviewDto;
import com.smartparking.booking.application.service.ReviewService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock
    private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "driver01", "DRIVER");
    }

    @Test
    @DisplayName("createReview returns CREATED status")
    void testCreateReview() {
        CreateReviewRequest req = new CreateReviewRequest();
        ReviewDto dto = ReviewDto.builder().id(1L).build();
        when(reviewService.createReview(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<ReviewDto>> response = reviewController.createReview(user, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("getReviews returns reviews for parking lot")
    void testGetReviews() {
        PageResponse<ReviewDto> page = new PageResponse<>(new PageImpl<>(List.of(ReviewDto.builder().id(1L).build())));
        when(reviewService.getReviews(eq(20L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<ReviewDto>>> response =
                reviewController.getReviews(20L, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }
}
