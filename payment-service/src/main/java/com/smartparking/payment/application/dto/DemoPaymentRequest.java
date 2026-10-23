package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoPaymentRequest {
    @NotNull(message = "bookingId is required")
    private Long bookingId;

    private Long merchantId;
    private Long parkingLotId;
    private BigDecimal amount;
}
