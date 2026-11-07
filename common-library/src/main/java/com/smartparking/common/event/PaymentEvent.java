package com.smartparking.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

public class PaymentEvent {

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class PaymentCompletedEvent extends DomainEvent {
        private Long paymentId;
        private Long bookingId;
        private Long userId;
        private BigDecimal amount;
        private String paymentMethod;
        private String paymentType;
        private String transactionRef;
    }

    @Data
    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class PaymentFailedEvent extends DomainEvent {
        private Long paymentId;
        private Long bookingId;
        private Long userId;
        private String reason;
    }
}
