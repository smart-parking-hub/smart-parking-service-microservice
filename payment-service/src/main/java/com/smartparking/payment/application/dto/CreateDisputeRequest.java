package com.smartparking.payment.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDisputeRequest {
    @NotNull(message = "Thiếu bookingId")
    private Long bookingId;

    private Long merchantId;

    @NotBlank(message = "Thiếu lý do khiếu nại")
    private String reason;

    private String description;
}
