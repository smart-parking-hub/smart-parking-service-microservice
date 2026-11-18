package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.domain.model.ProcessedEvent;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.events", groupId = "notification-service-group",
            containerFactory = "notificationEventsKafkaFactory")
    @Transactional
    public void handlePaymentEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            String eventType = root.has("eventType") ? root.get("eventType").asText() : null;

            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate payment event ignored: eventId={}", eventId);
                return;
            }

            Long userId = root.has("userId") && !root.get("userId").isNull() ? root.get("userId").asLong() : null;
            Long bookingId = root.has("bookingId") && !root.get("bookingId").isNull() ? root.get("bookingId").asLong() : null;
            String amount = root.has("amount") ? root.get("amount").asText() : "0";
            String paymentType = root.has("paymentType") && !root.get("paymentType").isNull() ? root.get("paymentType").asText() : "";

            if (userId != null && eventType != null) {
                switch (eventType) {
                    case "PAYMENT_COMPLETED" -> {
                        if ("DEPOSIT".equalsIgnoreCase(paymentType)) {
                            notificationService.createNotification(
                                    userId,
                                    "Đặt chỗ thành công",
                                    "Đặt chỗ thành công! Mã QR vào bãi đã sẵn sàng.",
                                    NotificationTypeEnum.BOOKING,
                                    "IN_APP",
                                    "BOOKING",
                                    bookingId
                            );
                        } else {
                            notificationService.createNotification(
                                    userId,
                                    "Thanh toán thành công",
                                    "Giao dịch thanh toán " + amount + " VND cho booking #" + bookingId + " đã thành công.",
                                    NotificationTypeEnum.PAYMENT,
                                    "IN_APP",
                                    "PAYMENT",
                                    bookingId
                            );
                        }
                    }
                    case "PAYMENT_FAILED" -> {
                        String reason = root.has("reason") ? root.get("reason").asText() : "Lỗi hệ thống";
                        notificationService.createNotification(
                                userId,
                                "Thanh toán thất bại",
                                "Giao dịch thanh toán cho booking #" + bookingId + " không thành công. Lý do: " + reason,
                                NotificationTypeEnum.PAYMENT,
                                "IN_APP",
                                "PAYMENT",
                                bookingId
                        );
                    }
                    default -> log.debug("Unhandled payment event type: {}", eventType);
                }
            }

            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType != null ? eventType : "PAYMENT_EVENT")
                        .build());
            }

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid payment event JSON", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist payment notifications", e);
        }
    }
}
