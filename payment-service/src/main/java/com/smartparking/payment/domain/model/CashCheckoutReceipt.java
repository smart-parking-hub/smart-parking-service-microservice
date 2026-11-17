package com.smartparking.payment.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_checkout_receipts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CashCheckoutReceipt {
    @Id @Column(name = "booking_id") private Long bookingId;
    @Column(name = "merchant_id", nullable = false) private Long merchantId;
    @Column(name = "parking_lot_id", nullable = false) private Long parkingLotId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
}
