package com.smartparking.payment.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.PaymentEvent;
import com.smartparking.payment.application.service.OutboxPublisherService;
import com.smartparking.payment.domain.model.OutboxEvent;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private static final String TOPIC_PAYMENT_EVENTS = "payment.events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishPaymentCompleted(Payment payment) {
        PaymentEvent.PaymentCompletedEvent event = PaymentEvent.PaymentCompletedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_COMPLETED")
                .aggregateId(String.valueOf(payment.getId()))
                .timestamp(Instant.now())
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getDriverId())
                .amount(payment.getAmount())
                .paymentMethod(payment.getGateway().name())
                .paymentType(payment.getPaymentType().name())
                .transactionRef(payment.getTransactionCode())
                .build();

        saveAndPublish(event, "Payment", String.valueOf(payment.getId()), "PAYMENT_COMPLETED");
    }

    @Override
    @Transactional
    public void publishPaymentFailed(Payment payment, String reason) {
        PaymentEvent.PaymentFailedEvent event = PaymentEvent.PaymentFailedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_FAILED")
                .aggregateId(String.valueOf(payment.getId()))
                .timestamp(Instant.now())
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getDriverId())
                .reason(reason)
                .build();

        saveAndPublish(event, "Payment", String.valueOf(payment.getId()), "PAYMENT_FAILED");
    }

    @Override
    @Transactional
    public void publishRefundStatusChanged(RefundRequest refundRequest) {
        if (refundRequest == null) {
            return;
        }
        PaymentEvent.RefundStatusChangedEvent event = PaymentEvent.RefundStatusChangedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PAYMENT_REFUND_UPDATED")
                .aggregateId(String.valueOf(refundRequest.getBookingId()))
                .timestamp(Instant.now())
                .refundRequestId(refundRequest.getId())
                .paymentId(refundRequest.getPaymentId())
                .bookingId(refundRequest.getBookingId())
                .userId(refundRequest.getDriverId())
                .merchantId(refundRequest.getMerchantId())
                .refundAmount(refundRequest.getRefundAmount())
                .refundPercentage(refundRequest.getRefundPercentage())
                .refundStatus(refundRequest.getStatus())
                .refundChannel(refundRequest.getRefundChannel())
                .policyCode(refundRequest.getPolicyCode())
                .transactionReference(refundRequest.getTransactionReference())
                .reason(refundRequest.getVerificationNote() != null
                        ? refundRequest.getVerificationNote()
                        : refundRequest.getCancellationReason())
                .build();

        saveAndPublish(event, "RefundRequest", String.valueOf(refundRequest.getBookingId()), "PAYMENT_REFUND_UPDATED");
    }

    private void saveAndPublish(Object event, String aggregateType, String aggregateId, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventId = (event instanceof com.smartparking.common.event.DomainEvent de && de.getEventId() != null)
                    ? de.getEventId()
                    : UUID.randomUUID().toString();

            OutboxEvent outbox = OutboxEvent.builder()
                    .id(eventId)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .status("PENDING")
                    .build();

            outboxEventRepository.save(outbox);

            // Asynchronously dispatch to Kafka AFTER current transaction commits cleanly
            Runnable dispatchTask = () -> dispatchToKafka(aggregateId, event, eventId, eventType);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                dispatchTask.run();
                            }
                        }
                );
            } else {
                dispatchTask.run();
            }
        } catch (Exception e) {
            log.error("Failed to serialize or save outbox event {}: {}", eventType, e.getMessage());
            throw new IllegalStateException("Failed to persist payment outbox event: " + eventType, e);
        }
    }

    private void dispatchToKafka(String aggregateId, Object event, String outboxId, String eventType) {
        if (kafkaTemplate != null) {
            kafkaTemplate.send(TOPIC_PAYMENT_EVENTS, aggregateId, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            outboxEventRepository.findById(outboxId).ifPresent(ob -> {
                                ob.setStatus("PUBLISHED");
                                ob.setProcessedAt(LocalDateTime.now());
                                outboxEventRepository.save(ob);
                            });
                            log.info("Successfully published {} event to topic {}", eventType, TOPIC_PAYMENT_EVENTS);
                        } else {
                            log.warn("Kafka publish failed for {}: {}. Retained in outbox.", eventType, ex.getMessage());
                        }
                    });
        }
    }
}
