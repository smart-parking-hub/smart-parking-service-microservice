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
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_COMPLETED");
    }

    @Override
    @Transactional
    public void publishBookingCancelled(Booking booking, String reason) {
        BookingEvent.BookingCancelledEvent event = BookingEvent.BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("BOOKING_CANCELLED")
                .aggregateId(String.valueOf(booking.getId()))
                .timestamp(Instant.now())
                .bookingId(booking.getId())
                .reason(reason)
                .build();

        saveAndPublish(event, "Booking", String.valueOf(booking.getId()), "BOOKING_CANCELLED");
    }

    private void saveAndPublish(Object event, String aggregateType, String aggregateId, String eventType) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            String eventId = UUID.randomUUID().toString();

            OutboxEvent outbox = OutboxEvent.builder()
                    .id(eventId)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .status("PENDING")
                    .build();

            outboxEventRepository.save(outbox);

            if (kafkaTemplate != null) {
                kafkaTemplate.send(TOPIC_BOOKING_EVENTS, aggregateId, event)
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                outbox.setStatus("PUBLISHED");
                                outbox.setProcessedAt(LocalDateTime.now());
                                outboxEventRepository.save(outbox);
                                log.info("Published {} event for booking {}", eventType, aggregateId);
                            } else {
                                log.warn("Kafka dispatch deferred for {}: {}", eventType, ex.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            log.error("Failed to save or publish outbox event {}: {}", eventType, e.getMessage());
        }
    }
}
