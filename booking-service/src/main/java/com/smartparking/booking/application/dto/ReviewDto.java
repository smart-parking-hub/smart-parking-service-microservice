package com.smartparking.booking.application.dto;

import com.smartparking.booking.domain.model.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDto {
    private Long id;
    private Long bookingId;
    private String bookingCode;
    private Long parkingLotId;
    private Long driverId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;

    public static ReviewDto fromEntity(Review review) {
        return ReviewDto.builder()
                .id(review.getId())
                .bookingId(review.getBooking().getId())
                .bookingCode(review.getBooking().getBookingCode())
                .parkingLotId(review.getParkingLotId())
                .driverId(review.getDriverId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
