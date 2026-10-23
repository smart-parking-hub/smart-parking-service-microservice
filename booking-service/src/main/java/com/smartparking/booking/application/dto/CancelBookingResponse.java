package com.smartparking.booking.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelBookingResponse {
    private Long bookingId;
    private String bookingCode;
    private String status;
    private boolean refundRequested;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String message;
}
