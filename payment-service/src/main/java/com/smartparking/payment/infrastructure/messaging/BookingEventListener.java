package com.smartparking.payment.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;
    private final WalletLedgerEntryRepository ledgerRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking.events", groupId = "payment-service-group")
    @Transactional
    public void handleBookingEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventType = root.has("eventType") ? root.get("eventType").asText() : null;
            Long bookingId = root.has("bookingId") && !root.get("bookingId").isNull() ? root.get("bookingId").asLong() : null;
            Long merchantId = root.has("merchantId") && !root.get("merchantId").isNull() ? root.get("merchantId").asLong() : null;

            if (bookingId == null || eventType == null) {
                return;
            }

            switch (eventType) {
                case "BOOKING_COMPLETED" -> {
                    // Ensure merchant wallet has been credited for all successful payments of this booking
                    paymentRepository.findByBookingId(bookingId).stream()
                            .filter(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)
                            .forEach(payment -> {
                                Long targetMerchantId = merchantId != null ? merchantId : payment.getMerchantId();
                                if (targetMerchantId != null) {
                                    boolean alreadyCredited = ledgerRepository.findByMerchantId(targetMerchantId).stream()
                                            .anyMatch(entry -> bookingId.equals(entry.getBookingId()));
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
                    log.info("Booking {} cancelled. Marking pending payments as FAILED.", bookingId);
                    paymentRepository.findByBookingId(bookingId).stream()
                            .filter(p -> p.getStatus() == PaymentStatusEnum.PENDING)
                            .forEach(p -> {
                                p.setStatus(PaymentStatusEnum.FAILED);
                                paymentRepository.save(p);
                                log.info("Marked pending payment {} as FAILED for cancelled booking {}", p.getId(), bookingId);
                            });
                }
                default -> log.debug("Unhandled booking event in payment-service: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process booking event in payment-service: {}", e.getMessage(), e);
        }
    }
}
