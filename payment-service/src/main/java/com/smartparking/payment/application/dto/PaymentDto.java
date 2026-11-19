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
public class PaymentDto {
    private Long id;
    private Long bookingId;
    private Long driverId;
    private Long merchantId;
    private Long parkingLotId;
    private PaymentGatewayEnum gateway;
    private String transactionCode;
    private Long orderCode;
    private BigDecimal amount;
    private PaymentTypeEnum type;
    private String paymentChannel;
    private PaymentStatusEnum status;
    private LocalDateTime createdAt;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundChannel;
    private String refundReference;
    // Populated only by an authoritative sync; URL return flags never grant retry permission.
    private boolean canRetry;
    private boolean canResume;

    public static PaymentDto fromEntity(Payment entity) {
        return PaymentDto.builder()
                .id(entity.getId())
                .bookingId(entity.getBookingId())
                .driverId(entity.getDriverId())
                .merchantId(entity.getMerchantId())
                .parkingLotId(entity.getParkingLotId())
                .gateway(entity.getGateway())
                .transactionCode(entity.getTransactionCode())
                .orderCode(entity.getOrderCode())
                .amount(entity.getAmount())
                .type(entity.getPaymentType())
                .paymentChannel(entity.getPaymentChannel())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
