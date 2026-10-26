package com.smartparking.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

public class BookingEvent {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class BookingCreatedEvent extends DomainEvent {
        private Long bookingId;
        private Long userId;
        private Long parkingLotId;
        private Long slotId;
        private String licensePlate;
        private Instant startTime;
        private Instant endTime;
        private BigDecimal totalAmount;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class BookingConfirmedEvent extends DomainEvent {
        private Long bookingId;
        private Long userId;
        private Long merchantId;
        private Long parkingLotId;
        private Long slotId;
        private String paymentRef;
        private BigDecimal totalAmount;
        private BigDecimal advanceDeposit;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class BookingCancelledEvent extends DomainEvent {
        private Long bookingId;
        private Long userId;
        private Long merchantId;
        private Long parkingLotId;
        private Long slotId;
        private String reason;
        private BigDecimal refundAmount;
    }
}
