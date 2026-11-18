package com.smartparking.notification.application.service;

import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.common.event.MerchantVerificationEvent;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantVerificationNotificationHandler {

    private final JdbcTemplate jdbc;
    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;

    @Transactional
    public void deliver(MerchantVerificationEvent event) {
        if (event == null || event.getSchemaVersion() != 1 || event.getEventId() == null
                || event.getEventId().isBlank() || event.getEventId().length() > 100
                || event.getMerchantUserId() == null || event.getMerchantUserId() <= 0
                || event.getDossierId() == null || event.getDossierId() <= 0
                || event.getType() == null
                || !(MerchantVerificationEvent.APPROVED.equals(event.getType())
                || MerchantVerificationEvent.CHANGES_REQUESTED.equals(event.getType()))) {
            throw new IllegalArgumentException("Invalid merchant verification event");
        }

        if (processed.existsByEventId(event.getEventId())) {
            log.info("Event {} already processed, skipping duplicate", event.getEventId());
            return;
        }

        // Deduplicate atomically
        jdbc.update("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)",
                event.getEventId(), event.getType(), java.sql.Timestamp.from(Instant.now()));

        boolean approved = MerchantVerificationEvent.APPROVED.equals(event.getType());
        String title = approved
                ? "Hồ sơ doanh nghiệp đã được phê duyệt"
                : "Yêu cầu bổ sung hồ sơ doanh nghiệp";
        String content = approved
                ? "Hồ sơ xác minh doanh nghiệp của bạn đã được quản trị viên phê duyệt thành công. Xem chi tiết tại Thông tin cá nhân."
                : "Quản trị viên đã yêu cầu bổ sung thông tin hồ sơ doanh nghiệp. Vui lòng kiểm tra và cập nhật lại tại Thông tin cá nhân.";

        Notification notification = Notification.builder()
                .userId(event.getMerchantUserId())
                .title(title)
                .content(content)
                .type(NotificationTypeEnum.SYSTEM)
                .channel("IN_APP")
                .targetType("MERCHANT_VERIFICATION")
                .targetId(event.getDossierId())
                .sourceEventId(event.getEventId())
                .build();

        notifications.saveAndFlush(notification);
        log.info("Delivered merchant verification notification to user {} for dossier {}",
                event.getMerchantUserId(), event.getDossierId());
    }
}
