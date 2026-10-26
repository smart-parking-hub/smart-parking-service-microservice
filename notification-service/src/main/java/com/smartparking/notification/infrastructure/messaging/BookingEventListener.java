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
public class BookingEventListener {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "booking.events", groupId = "notification-service-group")
    @Transactional
    public void handleBookingEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            String eventType = root.has("eventType") ? root.get("eventType").asText() : null;

            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate booking event ignored: eventId={}", eventId);
                return;
            }

            Long bookingId = root.has("bookingId") && !root.get("bookingId").isNull() ? root.get("bookingId").asLong() : null;
            Long userId = root.has("userId") && !root.get("userId").isNull() ? root.get("userId").asLong() : null;
            Long merchantId = root.has("merchantId") && !root.get("merchantId").isNull() ? root.get("merchantId").asLong() : null;

            if (userId != null && eventType != null) {
                switch (eventType) {
                    case "BOOKING_CREATED" -> {
                        String plate = root.has("licensePlate") ? root.get("licensePlate").asText() : "";
                        notificationService.createNotification(
                                userId,
                                "Đặt chỗ thành công",
                                "Lượt đặt chỗ mã #" + bookingId + " cho phương tiện " + plate + " đã được tạo thành công.",
                                NotificationTypeEnum.BOOKING,
                                "IN_APP",
                                "BOOKING",
                                bookingId
                        );
                        if (merchantId != null) {
                            notificationService.createNotification(
                                    merchantId,
                                    "Đơn đặt chỗ mới",
                                    "Bãi xe của bạn vừa có đơn đặt chỗ mới #" + bookingId + (plate.isEmpty() ? "" : " (Xe: " + plate + ")"),
                                    NotificationTypeEnum.BOOKING,
                                    "IN_APP",
                                    "BOOKING",
                                    bookingId
                            );
                        }
                    }
                    case "BOOKING_CHECKED_IN" -> {
                        notificationService.createNotification(
                                userId,
                                "Check-in thành công",
                                "Phương tiện của bạn đã hoàn tất check-in cho booking #" + bookingId + ".",
                                NotificationTypeEnum.BOOKING,
                                "IN_APP",
                                "BOOKING",
                                bookingId
                        );
                        if (merchantId != null) {
                            notificationService.createNotification(
                                    merchantId,
                                    "Xe đã vào bãi",
                                    "Khách hàng cho booking #" + bookingId + " đã hoàn tất check-in vào bãi đỗ.",
                                    NotificationTypeEnum.BOOKING,
                                    "IN_APP",
                                    "BOOKING",
                                    bookingId
                            );
                        }
                    }
                    case "BOOKING_COMPLETED" -> {
                        notificationService.createNotification(
                                userId,
                                "Hoàn tất đỗ xe",
                                "Lượt đỗ xe #" + bookingId + " đã hoàn tất. Cảm ơn bạn đã sử dụng SmartParking!",
                                NotificationTypeEnum.BOOKING,
                                "IN_APP",
                                "BOOKING",
                                bookingId
                        );
                        if (merchantId != null) {
                            notificationService.createNotification(
                                    merchantId,
                                    "Lượt đỗ xe hoàn tất",
                                    "Booking #" + bookingId + " đã check-out và giải phóng vị trí đỗ.",
                                    NotificationTypeEnum.BOOKING,
                                    "IN_APP",
                                    "BOOKING",
                                    bookingId
                            );
                        }
                    }
                    case "BOOKING_CANCELLED" -> {
                        String reason = root.has("reason") ? root.get("reason").asText() : "";
                        notificationService.createNotification(
                                userId,
                                "Đặt chỗ đã hủy",
                                "Booking #" + bookingId + " đã được hủy. Lý do: " + reason,
                                NotificationTypeEnum.BOOKING,
                                "IN_APP",
                                "BOOKING",
                                bookingId
                        );
                        if (merchantId != null) {
                            notificationService.createNotification(
                                    merchantId,
                                    "Khách hủy đặt chỗ",
                                    "Đơn đặt chỗ #" + bookingId + " đã bị hủy. Vị trí đỗ đã được mở lại cho khách khác.",
                                    NotificationTypeEnum.BOOKING,
                                    "IN_APP",
                                    "BOOKING",
                                    bookingId
                            );
                        }
                    }
                    default -> log.debug("Unhandled booking event type: {}", eventType);
                }
            }

            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType != null ? eventType : "BOOKING_EVENT")
                        .build());
            }

        } catch (Exception e) {
            log.error("Failed to process booking event in notification-service: {}", e.getMessage(), e);
        }
    }
}
