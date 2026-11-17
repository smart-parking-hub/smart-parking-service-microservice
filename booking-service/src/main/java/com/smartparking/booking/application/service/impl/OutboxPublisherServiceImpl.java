package com.smartparking.booking.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.model.OutboxEvent;
import com.smartparking.booking.domain.repository.OutboxEventRepository;
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

    private static final String TOPIC_BOOKING_EVENTS = "booking.events";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishBookingCreated(Booking booking) {
        BookingEvent.BookingCreatedEvent event = BookingEvent.BookingCreatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BOOKING_CREATED")
                .aggregateId(String.valueOf(booking.getId()))
                .timestamp(Instant.now())
                .bookingId(booking.getId())
                .userId(booking.getDriverId())
                .parkingLotId(booking.getParkingLotId())
                .licensePlate(booking.getVehiclePlate())
                .totalAmount(booking.getAdvanceDeposit())
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_CREATED");
    }

    @Override
    @Transactional
    public void publishBookingCheckedIn(Booking booking) {
        BookingEvent.BookingConfirmedEvent event = BookingEvent.BookingConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BOOKING_CHECKED_IN")
                .aggregateId(String.valueOf(booking.getId()))
                .timestamp(Instant.now())
                .bookingId(booking.getId())
                .userId(booking.getDriverId())
                .merchantId(booking.getMerchantId())
                .parkingLotId(booking.getParkingLotId())
                .totalAmount(booking.getTotalAmount())
                .advanceDeposit(booking.getAdvanceDeposit())
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_CHECKED_IN");
    }

    @Override
    @Transactional
    public void publishBookingCompleted(Booking booking) {
        BookingEvent.BookingConfirmedEvent event = BookingEvent.BookingConfirmedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BOOKING_COMPLETED")
                .aggregateId(String.valueOf(booking.getId()))
                .timestamp(Instant.now())
                .bookingId(booking.getId())
                .userId(booking.getDriverId())
                .merchantId(booking.getMerchantId())
                .parkingLotId(booking.getParkingLotId())
                .totalAmount(booking.getTotalAmount())
                .advanceDeposit(booking.getAdvanceDeposit())
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_COMPLETED");
    }

    @Override
    @Transactional
    public void publishBookingCancelled(Booking booking, String reason) {
        java.math.BigDecimal refundAmount = booking.getRefundAmount() != null
                ? booking.getRefundAmount()
                : java.math.BigDecimal.ZERO;
        Integer refundPercentage = booking.getRefundPercentage() != null ? booking.getRefundPercentage() : 0;
        String refundStatus = booking.getRefundStatus() != null ? booking.getRefundStatus() : "NONE";
        String refundPolicy = booking.getRefundPolicy() != null ? booking.getRefundPolicy() : "NO_REFUND_UNPAID";
        Instant cancelledInstant = booking.getCancelledAt() != null
                ? booking.getCancelledAt().atZone(com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE).toInstant()
                : Instant.now();
        Instant checkInInstant = booking.getExpectedCheckInTime() != null
                ? booking.getExpectedCheckInTime().atZone(com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE).toInstant()
                : null;
        String eventId = UUID.randomUUID().toString();

        BookingEvent.BookingCancelledEvent event = BookingEvent.BookingCancelledEvent.builder()
                .eventId(eventId)
                .eventType("BOOKING_CANCELLED")
                .aggregateId(String.valueOf(booking.getId()))
                .timestamp(Instant.now())
                .bookingId(booking.getId())
                .userId(booking.getDriverId())
                .merchantId(booking.getMerchantId())
                .parkingLotId(booking.getParkingLotId())
                .reason(reason)
                .advanceDeposit(booking.getAdvanceDeposit())
                .refundAmount(refundAmount)
                .refundPercentage(refundPercentage)
                .refundRequested(refundAmount.signum() > 0)
                .refundStatus(refundStatus)
                .refundChannel(booking.getRefundChannel())
                .refundPolicy(refundPolicy)
                .currency("VND")
                .cancelledAt(cancelledInstant)
                .expectedCheckInTime(checkInInstant)
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_CANCELLED");
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

            // Flush inside the caller's transaction so INSERT/constraint failures surface here,
            // before any non-transactional side effect (e.g. HTTP releaseSlot) is attempted.
            // Kafka dispatch stays in afterCommit; flush does not make HTTP calls transactional.
            outboxEventRepository.saveAndFlush(outbox);

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
            log.error("Failed to save or serialize outbox event {}: {}", eventType, e.getMessage());
            throw new IllegalStateException("Failed to persist transactional outbox event: " + eventType, e);
        }
    }

    private void dispatchToKafka(String aggregateId, Object event, String outboxId, String eventType) {
        if (kafkaTemplate != null) {
            kafkaTemplate.send(TOPIC_BOOKING_EVENTS, aggregateId, event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            outboxEventRepository.findById(outboxId).ifPresent(ob -> {
                                ob.setStatus("PUBLISHED");
                                ob.setProcessedAt(LocalDateTime.now());
                                outboxEventRepository.save(ob);
                            });
                            log.info("Published {} event for booking {}", eventType, aggregateId);
                        } else {
                            log.warn("Kafka dispatch deferred for {}: {}", eventType, ex.getMessage());
                        }
                    });
        }
    }
}
