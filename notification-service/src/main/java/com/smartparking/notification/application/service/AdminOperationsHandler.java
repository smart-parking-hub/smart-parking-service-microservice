package com.smartparking.notification.application.service;

import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class AdminOperationsHandler {
    private final JdbcTemplate jdbc;
    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;
    @Value("${app.admin-operations.recipient-max-age-seconds:180}") private long maxAgeSeconds = 180;
    @Value("${app.admin-operations.max-recipients:1000}") private int maxRecipients = 1000;

    @Transactional
    public void applySnapshot(AdminRecipientSnapshot snapshot) {
        require(snapshot != null && snapshot.getSchemaVersion() == 1
                && AdminRecipientSnapshot.TYPE.equals(snapshot.getEventType())
                && snapshot.getSnapshotVersion() > 0 && snapshot.getTimestamp() != null
                && !snapshot.getTimestamp().isAfter(Instant.now().plusSeconds(60)), "Invalid recipient snapshot");
        var ids = snapshot.getRecipientIds();
        require(ids != null && ids.size() <= maxRecipients && ids.stream().allMatch(id -> id != null && id > 0)
                && new HashSet<>(ids).size() == ids.size(), "Invalid recipient IDs");
        var state = jdbc.queryForMap("SELECT * FROM admin_recipient_state WHERE id = 1 FOR UPDATE");
        if (((Number) state.get("snapshot_version")).longValue() >= snapshot.getSnapshotVersion()) return;
        jdbc.update("DELETE FROM admin_notification_recipients");
        for (Long id : ids) jdbc.update("INSERT INTO admin_notification_recipients (user_id) VALUES (?)", id);
        jdbc.update("UPDATE admin_recipient_state SET snapshot_version = ?, source_created_at = ? WHERE id = 1",
                snapshot.getSnapshotVersion(), Timestamp.from(snapshot.getTimestamp()));
    }

    @Transactional
    public void deliver(AdminOperationEvent event) {
        validate(event);
        if (processed.existsByEventId(event.getEventId())) return;
        var state = jdbc.queryForMap("SELECT * FROM admin_recipient_state WHERE id = 1 FOR UPDATE");
        // The recipient-state lock also serializes concurrent fan-outs. Recheck
        // after acquiring it, since another consumer may have just committed.
        if (processed.existsByEventId(event.getEventId())) return;
        Timestamp captured = (Timestamp) state.get("source_created_at");
        if (captured == null || captured.toInstant().isBefore(Instant.now().minusSeconds(maxAgeSeconds))) {
            throw new IllegalStateException("Admin recipient snapshot is unavailable or stale");
        }
        var ids = jdbc.queryForList("SELECT user_id FROM admin_notification_recipients ORDER BY user_id", Long.class);
        if (ids.isEmpty()) throw new IllegalStateException("No eligible Admin recipients; event must be retried or replayed");
        // Claim with INSERT (not JPA merge). A concurrent duplicate rolls back and
        // is retried; source_event_id/user_id also protects each delivered record.
        jdbc.update("INSERT INTO processed_events (event_id, event_type, processed_at) VALUES (?, ?, ?)",
                event.getEventId(), event.getEventType(), Timestamp.from(Instant.now()));
        String title;
        String content;
        String target;
        NotificationTypeEnum type = NotificationTypeEnum.SYSTEM;
        switch (event.getEventType()) {
            case AdminOperationEvent.WITHDRAWAL_REQUESTED -> {
                target = "WITHDRAWAL";
                type = NotificationTypeEnum.PAYMENT;
                title = "Yêu cầu rút tiền mới #" + event.getTargetId();
                content = "Yêu cầu rút " + event.getAmount().toPlainString() + " VND đang chờ xử lý. Kiểm tra tại mục Duyệt rút tiền.";
            }
            case AdminOperationEvent.DISPUTE_OPENED -> {
                target = "DISPUTE";
                title = "Tranh chấp mới #" + event.getTargetId();
                content = "Có tranh chấp mới cần xem xét. Kiểm tra tại mục Tranh chấp.";
            }
            case AdminOperationEvent.LOT_REVIEW_REQUESTED -> {
                target = "PARKING_LOT";
                title = "Hồ sơ bãi đỗ chờ duyệt #" + event.getTargetId();
                content = "Hồ sơ được nộp để kiểm duyệt (lần " + event.getSubmissionVersion() + "). Kiểm tra tại mục Kiểm duyệt bãi.";
            }
            default -> throw new IllegalArgumentException("Unsupported admin operation");
        }
        final String notificationTitle = title;
        final String notificationContent = content;
        final String targetType = target;
        final NotificationTypeEnum notificationType = type;
        notifications.saveAllAndFlush(ids.stream().map(id -> Notification.builder().userId(id)
                .title(notificationTitle).content(notificationContent).type(notificationType).channel("IN_APP")
                .targetType(targetType).targetId(event.getTargetId()).sourceEventId(event.getEventId()).build()).toList());
    }

    private void validate(AdminOperationEvent event) {
        require(event != null && event.getSchemaVersion() == 1 && event.getEventId() != null
                && !event.getEventId().isBlank() && event.getEventId().length() <= 100
                && event.getTargetId() != null && event.getTargetId() > 0
                && event.getActorId() != null && event.getActorId() > 0
                && event.getTimestamp() != null && !event.getTimestamp().isAfter(Instant.now().plusSeconds(60)),
                "Invalid admin operation envelope");
        String type = event.getEventType();
        require(AdminOperationEvent.WITHDRAWAL_REQUESTED.equals(type) || AdminOperationEvent.DISPUTE_OPENED.equals(type)
                || AdminOperationEvent.LOT_REVIEW_REQUESTED.equals(type), "Unsupported admin operation");
        if (AdminOperationEvent.WITHDRAWAL_REQUESTED.equals(type)) {
            require(event.getAmount() != null && event.getAmount().signum() > 0 && "VND".equals(event.getCurrency()), "Invalid withdrawal amount");
        }
        if (AdminOperationEvent.LOT_REVIEW_REQUESTED.equals(type)) {
            require(event.getSubmissionVersion() != null && event.getSubmissionVersion() > 0, "Invalid submission version");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
