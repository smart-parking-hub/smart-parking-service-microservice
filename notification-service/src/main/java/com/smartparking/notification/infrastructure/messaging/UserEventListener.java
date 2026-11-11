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

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "user.events", groupId = "notification-service-group")
    @Transactional
    public void handleUserEvent(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String eventId = root.has("eventId") ? root.get("eventId").asText() : null;
            String eventType = root.has("eventType") ? root.get("eventType").asText() : null;

            if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
                log.info("Duplicate user event ignored: eventId={}", eventId);
                return;
            }

            Long userId = root.has("userId") && !root.get("userId").isNull() ? root.get("userId").asLong() : null;
            Long warningId = root.has("warningId") && !root.get("warningId").isNull() ? root.get("warningId").asLong() : null;
            String reason = root.has("reason") ? root.get("reason").asText() : "Vi phạm quy định nền tảng";
            int warningCount = root.has("warningCount") ? root.get("warningCount").asInt() : 1;
            boolean isSuspended = root.has("isSuspended") && root.get("isSuspended").asBoolean();

            if (userId != null && "USER_WARNED".equals(eventType)) {
                String title;
                String content;

                if (isSuspended) {
                    title = "Tài khoản đã bị tạm khóa 7 ngày";
                    content = "Tài khoản của bạn đã bị tạm khóa 7 ngày do đạt 3 lần cảnh cáo vi phạm. Lý do: " + reason;
                } else {
                    title = "Cảnh báo vi phạm tài khoản (" + warningCount + "/3)";
                    content = "Bạn vừa nhận một cảnh cáo từ Quản trị viên. Lý do: " + reason + ". Đạt 3 lần cảnh cáo tài khoản sẽ bị tạm khóa 7 ngày.";
                }

                notificationService.createNotification(
                        userId,
                        title,
                        content,
                        NotificationTypeEnum.SYSTEM,
                        "IN_APP",
                        "USER_WARNING",
                        warningId
                );
            }

            if (eventId != null) {
                processedEventRepository.save(ProcessedEvent.builder()
                        .eventId(eventId)
                        .eventType(eventType)
                        .processedAt(LocalDateTime.now())
                        .build());
            }
        } catch (Exception e) {
            log.error("Failed to process user event: payload={}", payload, e);
        }
    }
}

