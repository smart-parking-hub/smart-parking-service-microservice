package com.smartparking.payment.domain.model;

import com.smartparking.common.enums.PaymentGatewayEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "parking_lot_id")
    private Long parkingLotId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentGatewayEnum gateway;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 30)
    private PaymentTypeEnum paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatusEnum status;

    @Column(name = "transaction_code", unique = true, length = 100)
    private String transactionCode;

    @Column(name = "order_code", unique = true)
    private Long orderCode;

    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;
    @Column(name = "checkout_url", length = 2048)
    private String checkoutUrl;
    @Column(name = "booking_expires_at")
    private java.time.LocalDateTime bookingExpiresAt;
    @Column(name = "review_reason", length = 255)
    private String reviewReason;
    @Column(name = "wallet_refund", nullable = false)
    private boolean walletRefund;

    @Builder.Default
    @Column(name = "payment_channel", nullable = false, length = 30)
    private String paymentChannel = "UNKNOWN";

    @PrePersist
    protected void ensurePaymentChannel() {
        if (paymentChannel == null || paymentChannel.isBlank()) {
            paymentChannel = "UNKNOWN";
        }
    }
}
