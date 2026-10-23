package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
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
public class CreatePaymentRequest {

    @NotNull(message = "Thiếu bookingId")
    private Long bookingId;

    // Optional fields when called via inter-service RPC
    private Long merchantId;
    private Long parkingLotId;
    private BigDecimal amount;

    // DEPOSIT or FINAL_PAYMENT
    @NotBlank(message = "Thiếu paymentType")
    @Builder.Default
    private String paymentType = "DEPOSIT";
}
