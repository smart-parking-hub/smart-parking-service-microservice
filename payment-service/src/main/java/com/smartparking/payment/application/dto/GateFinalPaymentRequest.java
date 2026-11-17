package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GateFinalPaymentRequest {
    @NotNull(message = "bookingId không được để trống")
    private Long bookingId;
}
