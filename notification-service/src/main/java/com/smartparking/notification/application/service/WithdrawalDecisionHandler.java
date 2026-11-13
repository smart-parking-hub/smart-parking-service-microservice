package com.smartparking.notification.application.service;

import com.smartparking.common.event.WithdrawalDecisionEvent;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalDecisionHandler {
    private final JdbcTemplate jdbc;
    private final NotificationRepository notifications;
    private final ProcessedEventRepository processed;

    @Transactional
    public void deliver(WithdrawalDecisionEvent event) {
        if (event == null || event.getSchemaVersion() != 1 || event.getEventId() == null
                || event.getEventId().isBlank() || event.getEventId().length() > 100
                || event.getMerchantId() == null || event.getMerchantId() <= 0
                || event.getWithdrawalId() == null || event.getWithdrawalId() <= 0
                || event.getAmount() == null || event.getAmount().signum() <= 0
                || !"VND".equals(event.getCurrency()) || event.getTimestamp() == null
                || event.getTimestamp().isAfter(java.time.Instant.now().plusSeconds(60))
                || !(WithdrawalDecisionEvent.APPROVED.equals(event.getEventType())
                || WithdrawalDecisionEvent.REJECTED.equals(event.getEventType()))) {
            throw new IllegalArgumentException("Invalid withdrawal decision");
        }
        if (processed.existsByEventId(event.getEventId())) return;
        // A concurrent duplicate fails this unique insert and is retried; no partial notification commits.
        jdbc.update("INSERT INTO processed_events (event_id,event_type,processed_at) VALUES (?,?,?)",
                event.getEventId(), event.getEventType(), java.sql.Timestamp.from(java.time.Instant.now()));
        boolean approved = WithdrawalDecisionEvent.APPROVED.equals(event.getEventType());
        String amount = new java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols.getInstance(
                java.util.Locale.forLanguageTag("vi-VN"))).format(event.getAmount());
        notifications.saveAndFlush(Notification.builder().userId(event.getMerchantId())
                .title((approved ? "Yêu cầu rút tiền đã được duyệt #" : "Yêu cầu rút tiền bị từ chối #") + event.getWithdrawalId())
                .content("Yêu cầu rút " + amount + " VND " + (approved ? "đã được quản trị viên phê duyệt."
                        : "đã bị từ chối; khoản tiền giữ đã được hoàn lại vào ví.") + " Xem chi tiết tại Ví & Rút tiền.")
                .type(NotificationTypeEnum.PAYMENT).channel("IN_APP").targetType("WITHDRAWAL")
                .targetId(event.getWithdrawalId()).sourceEventId(event.getEventId()).build());
    }
}
