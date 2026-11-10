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
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.smartparking.booking.domain.repository.ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "payment.events", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentEvent(Object payload) {
        try {
            log.info("Received payment event: {}", payload);
            if (payload instanceof org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
                payload = record.value();
            }

            if (payload instanceof PaymentEvent.RefundStatusChangedEvent refundEvent) {
                handleRefundStatusChanged(refundEvent);
                return;
            }
            if (payload instanceof String str && str.contains("PAYMENT_REFUND_UPDATED")) {
                PaymentEvent.RefundStatusChangedEvent refundEvent =
                        objectMapper.readValue(str, PaymentEvent.RefundStatusChangedEvent.class);
                handleRefundStatusChanged(refundEvent);
                return;
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

            if (isDuplicateAndRecord(event.getEventId(), "PAYMENT_COMPLETED", String.valueOf(event.getBookingId()))) {
                log.info("Skipping duplicate PAYMENT_COMPLETED event {}", event.getEventId());
                return;
            }

            bookingRepository.findByIdForUpdate(event.getBookingId()).ifPresent(booking -> {
                if (booking.getStatus() == BookingStatusEnum.PENDING_PAYMENT && "DEPOSIT".equals(event.getPaymentType())) {
                    if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(LocalDateTime.now())) {
                        log.error("Paid event arrived after hold expiry; reconciliation required: booking={}", booking.getId());
                        return;
                    }
                    if (event.getAmount() == null || booking.getAdvanceDeposit() == null || event.getAmount().compareTo(booking.getAdvanceDeposit()) != 0) {
                        log.error("Payment amount does not match booking deposit: booking={}", booking.getId());
                        return;
                    }
                    booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);

                    // QR code is valid until expectedCheckInTime + 60 minutes (or at least 2 hours from now)
                    LocalDateTime deadline = booking.getExpectedCheckInTime() != null
                            ? booking.getExpectedCheckInTime().plusMinutes(60)
                            : LocalDateTime.now().plusHours(2);
                    booking.setExpiresAt(deadline);
                    bookingRepository.save(booking);
                    log.info("Booking {} transitioned to PENDING_CHECKIN after deposit payment, expiresAt={}", booking.getBookingCode(), deadline);
                } else if (booking.getStatus() == BookingStatusEnum.AWAITING_FINAL_PAYMENT && "FINAL_PAYMENT".equals(event.getPaymentType())) {
                    if (event.getAmount() == null || booking.getTotalAmount() == null || booking.getAdvanceDeposit() == null || event.getAmount().compareTo(booking.getTotalAmount().subtract(booking.getAdvanceDeposit()).max(java.math.BigDecimal.ZERO)) != 0) return;
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
                        throw new IllegalStateException("Final settlement slot release failed", ex);
                    }

                    log.info("Booking {} transitioned to COMPLETED after final payment", booking.getBookingCode());
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Payment event must be retried", e);
        }
    }

    private void handleRefundStatusChanged(PaymentEvent.RefundStatusChangedEvent event) {
        if (event == null || event.getBookingId() == null || event.getRefundStatus() == null) {
            return;
        }
        if (isDuplicateAndRecord(event.getEventId(), "PAYMENT_REFUND_UPDATED", String.valueOf(event.getBookingId()))) {
            log.info("Skipping duplicate PAYMENT_REFUND_UPDATED event {}", event.getEventId());
            return;
        }
        bookingRepository.findByIdForUpdate(event.getBookingId()).ifPresent(booking -> {
            booking.setRefundStatus(event.getRefundStatus());
            if (event.getTransactionReference() != null && !event.getTransactionReference().isBlank()) {
                booking.setRefundReference(event.getTransactionReference());
            }
            bookingRepository.save(booking);
            log.info("Updated booking {} refund status to {}", booking.getBookingCode(), event.getRefundStatus());
        });
    }

    private boolean isDuplicateAndRecord(String eventId, String eventType, String aggregateId) {
        if (processedEventRepository == null || eventId == null || eventId.isBlank()) {
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            return true;
        }
        processedEventRepository.save(com.smartparking.booking.domain.model.ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateId(aggregateId)
                .processedAt(LocalDateTime.now())
                .build());
        return false;
    }
}
