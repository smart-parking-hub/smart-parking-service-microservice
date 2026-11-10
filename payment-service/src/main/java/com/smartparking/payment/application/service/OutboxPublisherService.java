package com.smartparking.payment.application.service;

import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;

public interface OutboxPublisherService {
    void publishPaymentCompleted(Payment payment);
    void publishPaymentFailed(Payment payment, String reason);

    default void publishRefundStatusChanged(RefundRequest refundRequest) {
    }
}
