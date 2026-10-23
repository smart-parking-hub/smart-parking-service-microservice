package com.smartparking.payment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncPaymentRequest {
    private Long orderCode;
    private String paymentLinkId;
    private Long bookingId;
}
