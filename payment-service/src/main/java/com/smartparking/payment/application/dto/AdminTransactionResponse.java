package com.smartparking.payment.application.dto;

import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.payment.domain.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTransactionResponse {
    private Long id;
    private String transactionCode;
    private Long bookingId;
    private String bookingCode;
    private Long payerUserId;
    private String payerPhone;
    private Long merchantId;
    private String merchantName;
    private PaymentGatewayEnum gateway;
    private BigDecimal amount;
    private PaymentTypeEnum type;
    private PaymentStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AdminTransactionResponse from(Payment p) {
        return AdminTransactionResponse.builder()
                .id(p.getId())
                .transactionCode(p.getTransactionCode() != null ? p.getTransactionCode() : "TX-" + p.getId())
                .bookingId(p.getBookingId())
                .bookingCode("BK-" + p.getBookingId())
                .payerUserId(p.getDriverId())
                .payerPhone(p.getDriverId() != null ? "USER#" + p.getDriverId() : null)
                .merchantId(p.getMerchantId())
                .merchantName(p.getMerchantId() != null ? "Merchant #" + p.getMerchantId() : null)
                .gateway(p.getGateway())
                .amount(p.getAmount())
                .type(p.getPaymentType())
                .status(p.getStatus())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
