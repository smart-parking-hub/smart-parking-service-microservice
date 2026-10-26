package com.smartparking.booking.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.event.PaymentEvent;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final BookingRepository bookingRepository;
    private final ParkingLotClient parkingLotClient;
    private final ObjectMapper objectMapper;
    private final com.smartparking.booking.application.service.QueueService queueService;

    @KafkaListener(topics = "payment.events", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentEvent(Object payload) {
        try {
            log.info("Received payment event: {}", payload);
            if (payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
                payload = record.value();
            }

            final PaymentEvent.PaymentCompletedEvent event;
            if (payload instanceof PaymentEvent.PaymentCompletedEvent completedEvent) {
                event = completedEvent;
            } else if (payload instanceof String str) {
                event = objectMapper.readValue(str, PaymentEvent.PaymentCompletedEvent.class);
            } else if (payload != null) {
                event = objectMapper.convertValue(payload, PaymentEvent.PaymentCompletedEvent.class);
            } else {
                event = null;
            }

            if (event == null || event.getBookingId() == null) {
                return;
            }

            bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
                if (booking.getStatus() == BookingStatusEnum.PENDING_PAYMENT) {
                    booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
                    booking.setAdvanceDeposit(event.getAmount());
                    // QR code is valid until expectedCheckInTime + 60 minutes (or at least 2 hours from now)
                    LocalDateTime deadline = booking.getExpectedCheckInTime() != null
                            ? booking.getExpectedCheckInTime().plusMinutes(60)
                            : LocalDateTime.now().plusHours(2);
                    booking.setExpiresAt(deadline);
                    bookingRepository.save(booking);
                    log.info("Booking {} transitioned to PENDING_CHECKIN after deposit payment, expiresAt={}", booking.getBookingCode(), deadline);
                } else if (booking.getStatus() == BookingStatusEnum.AWAITING_FINAL_PAYMENT) {
                    booking.setStatus(BookingStatusEnum.COMPLETED);
                    bookingRepository.save(booking);

                    try {
                        SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                                .vehicleType(booking.getVehicleType())
                                .bookingId(booking.getId())
                                .build();
                        parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
                        queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
                    } catch (Exception ex) {
                        log.warn("Failed to notify parking-lot-service to release slot: {}", ex.getMessage());
                    }

                    log.info("Booking {} transitioned to COMPLETED after final payment", booking.getBookingCode());
                }
            });
        } catch (Exception e) {
            log.error("Error processing payment event in booking-service: {}", e.getMessage());
        }
    }
}
