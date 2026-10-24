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

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final BookingRepository bookingRepository;
    private final ParkingLotClient parkingLotClient;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.events", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentEvent(String payload) {
        try {
            log.info("Received payment event: {}", payload);
            PaymentEvent.PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentEvent.PaymentCompletedEvent.class);

            if (event.getBookingId() == null) {
                return;
            }

            bookingRepository.findById(event.getBookingId()).ifPresent(booking -> {
                if (booking.getStatus() == BookingStatusEnum.PENDING_PAYMENT) {
                    booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
                    booking.setAdvanceDeposit(event.getAmount());
                    bookingRepository.save(booking);
                    log.info("Booking {} transitioned to PENDING_CHECKIN after deposit payment", booking.getBookingCode());
                } else if (booking.getStatus() == BookingStatusEnum.AWAITING_FINAL_PAYMENT) {
                    booking.setStatus(BookingStatusEnum.COMPLETED);
                    bookingRepository.save(booking);

                    try {
                        SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                                .vehicleType(booking.getVehicleType())
                                .bookingId(booking.getId())
                                .build();
                        parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
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
