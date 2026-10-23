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
public class CheckOutResponse {
    private String bookingCode;
    private BigDecimal totalAmount;
    private BigDecimal advanceDeposit;
    private BigDecimal amountDue;
    private Long parkingMinutes;
    private String bookingStatus;
}
