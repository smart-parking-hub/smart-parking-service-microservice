package com.smartparking.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.RefundService;
import com.smartparking.payment.domain.model.ProcessedEvent;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.ProcessedEventRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;
    private final WalletLedgerEntryRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private RefundService refundService;

    @Autowired(required = false)
    private ProcessedEventRepository processedEventRepository;

    @KafkaListener(topics = "booking.events", groupId = "payment-service-group")
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void handleBookingEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.has("eventId") && !root.get("eventId").isNull() ? root.get("eventId").asText() : null;
            String eventType = root.has("eventType") && !root.get("eventType").isNull() ? root.get("eventType").asText() : null;
            Long bookingId = root.has("bookingId") && !root.get("bookingId").isNull() ? root.get("bookingId").asLong() : null;
            Long merchantId = root.has("merchantId") && !root.get("merchantId").isNull() ? root.get("merchantId").asLong() : null;

            if (bookingId == null || eventType == null) {
                return;
            }

            if (eventId != null && !eventId.isBlank() && processedEventRepository != null) {
                if (processedEventRepository.existsById(eventId)) {
                    log.info("Skipping duplicate booking event {} ({}) for booking {}", eventId, eventType, bookingId);
                    return;
                }
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .aggregateId(String.valueOf(bookingId))
                        .processedAt(LocalDateTime.now())
                        .build());
            }

            switch (eventType) {
                case "BOOKING_COMPLETED" -> {
                    paymentRepository.findByBookingId(bookingId).stream()
                            .filter(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)
                            .forEach(payment -> {
                                Long targetMerchantId = merchantId != null ? merchantId : payment.getMerchantId();
                                if (targetMerchantId != null) {
                                    boolean alreadyCredited = ledgerRepository.findByMerchantId(targetMerchantId).stream()
                                            .anyMatch(entry -> payment.getId() != null && entry.getPayment() != null
                                                    ? payment.getId().equals(entry.getPayment().getId())
                                                    : bookingId.equals(entry.getBookingId()));
                                    if (!alreadyCredited) {
                                        merchantWalletService.creditSettlement(
                                                targetMerchantId,
                                                bookingId,
                                                payment,
                                                payment.getAmount(),
                                                "Kết toán hoàn tất lượt đỗ booking #" + bookingId);
                                        log.info("Settled payment {} for completed booking {}", payment.getId(), bookingId);
                                    }
                                }
                            });
                }
                case "BOOKING_CANCELLED" -> {
                    log.info("Booking {} cancelled. Processing pending payments and refund policy.", bookingId);
                    paymentRepository.findByBookingId(bookingId).stream()
                            .filter(p -> p.getStatus() == PaymentStatusEnum.PENDING && p.getGateway() != com.smartparking.common.enums.PaymentGatewayEnum.WALLET)
                            .forEach(p -> {
                                p.setStatus(PaymentStatusEnum.FAILED);
                                paymentRepository.save(p);
                                log.info("Marked pending payment {} as FAILED for cancelled booking {}", p.getId(), bookingId);
                            });

                    if (refundService != null) {
                        BigDecimal refundAmount = root.has("refundAmount") && !root.get("refundAmount").isNull()
                                ? root.get("refundAmount").decimalValue()
                                : BigDecimal.ZERO;
                        BigDecimal advanceDeposit = root.has("advanceDeposit") && !root.get("advanceDeposit").isNull()
                                ? root.get("advanceDeposit").decimalValue()
                                : refundAmount;
                        Integer refundPercentage = root.has("refundPercentage") && !root.get("refundPercentage").isNull()
                                ? root.get("refundPercentage").asInt()
                                : (refundAmount.signum() > 0 ? 100 : 0);
                        Boolean refundRequested = root.has("refundRequested") && !root.get("refundRequested").isNull()
                                ? root.get("refundRequested").asBoolean()
                                : refundAmount.signum() > 0;
                        String refundPolicy = root.has("refundPolicy") && !root.get("refundPolicy").isNull()
                                ? root.get("refundPolicy").asText()
                                : (refundAmount.signum() > 0 ? "FULL_100_AT_OR_BEFORE_2H" : "NO_REFUND");
                        String refundStatus = root.has("refundStatus") && !root.get("refundStatus").isNull()
                                ? root.get("refundStatus").asText()
                                : (refundAmount.signum() > 0 ? "REQUESTED" : "NONE");
                        Long userId = root.has("userId") && !root.get("userId").isNull() ? root.get("userId").asLong() : null;
                        Long parkingLotId = root.has("parkingLotId") && !root.get("parkingLotId").isNull() ? root.get("parkingLotId").asLong() : null;
                        String reason = root.has("reason") && !root.get("reason").isNull() ? root.get("reason").asText() : null;

                        BookingEvent.BookingCancelledEvent cancelledEvent = BookingEvent.BookingCancelledEvent.builder()
                                .eventId(eventId)
                                .eventType(eventType)
                                .bookingId(bookingId)
                                .userId(userId)
                                .merchantId(merchantId)
                                .parkingLotId(parkingLotId)
                                .reason(reason)
                                .advanceDeposit(advanceDeposit)
                                .refundAmount(refundAmount)
                                .refundPercentage(refundPercentage)
                                .refundRequested(refundRequested)
                                .refundPolicy(refundPolicy)
                                .refundStatus(refundStatus)
                                .refundChannel(root.path("refundChannel").asText("BANK_MANUAL"))
                                .currency("VND")
                                .build();

                        refundService.handleBookingCancellationRefund(cancelledEvent);
                    }
                }
                default -> log.debug("Unhandled booking event in payment-service: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process booking event in payment-service: {}", e.getMessage(), e);
            throw new IllegalStateException("Booking event processing failed; retry required", e);
        }
    }
}
