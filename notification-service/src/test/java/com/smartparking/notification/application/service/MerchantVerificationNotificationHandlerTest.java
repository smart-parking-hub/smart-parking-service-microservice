package com.smartparking.notification.application.service;

import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.common.event.MerchantVerificationEvent;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantVerificationNotificationHandlerTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private NotificationRepository notifications;

    @Mock
    private ProcessedEventRepository processed;

    private MerchantVerificationNotificationHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MerchantVerificationNotificationHandler(jdbc, notifications, processed);
    }

    @Test
    @DisplayName("Delivers APPROVED event to user and creates notification with targetType=MERCHANT_VERIFICATION")
    void deliversApprovedEvent() {
        String eventId = UUID.randomUUID().toString();
        MerchantVerificationEvent event = MerchantVerificationEvent.builder()
                .eventId(eventId)
                .schemaVersion(1)
                .type(MerchantVerificationEvent.APPROVED)
                .merchantUserId(42L)
                .dossierId(100L)
                .submissionVersion(1)
                .occurredAt(Instant.now())
                .build();

        when(processed.existsByEventId(eventId)).thenReturn(false);

        handler.deliver(event);

        verify(jdbc).update(anyString(), eq(eventId), eq(MerchantVerificationEvent.APPROVED), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).saveAndFlush(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getTargetType()).isEqualTo("MERCHANT_VERIFICATION");
        assertThat(saved.getTargetId()).isEqualTo(100L);
        assertThat(saved.getSourceEventId()).isEqualTo(eventId);
        assertThat(saved.getTitle()).contains("phê duyệt");
        assertThat(saved.getType()).isEqualTo(NotificationTypeEnum.SYSTEM);
    }

    @Test
    @DisplayName("Delivers CHANGES_REQUESTED event and informs merchant")
    void deliversChangesRequestedEvent() {
        String eventId = UUID.randomUUID().toString();
        MerchantVerificationEvent event = MerchantVerificationEvent.builder()
                .eventId(eventId)
                .schemaVersion(1)
                .type(MerchantVerificationEvent.CHANGES_REQUESTED)
                .merchantUserId(42L)
                .dossierId(100L)
                .submissionVersion(2)
                .occurredAt(Instant.now())
                .build();

        when(processed.existsByEventId(eventId)).thenReturn(false);

        handler.deliver(event);

        verify(jdbc).update(anyString(), eq(eventId), eq(MerchantVerificationEvent.CHANGES_REQUESTED), any());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).saveAndFlush(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getTargetType()).isEqualTo("MERCHANT_VERIFICATION");
        assertThat(saved.getTitle()).contains("bổ sung");
    }

    @Test
    @DisplayName("Deduplication: replaying an event does not create duplicate notification")
    void deduplicatesReplayedEvent() {
        String eventId = UUID.randomUUID().toString();
        MerchantVerificationEvent event = MerchantVerificationEvent.builder()
                .eventId(eventId)
                .schemaVersion(1)
                .type(MerchantVerificationEvent.APPROVED)
                .merchantUserId(42L)
                .dossierId(100L)
                .submissionVersion(1)
                .occurredAt(Instant.now())
                .build();

        when(processed.existsByEventId(eventId)).thenReturn(true);

        handler.deliver(event);

        verify(notifications, never()).saveAndFlush(any());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Rejects invalid event envelope")
    void rejectsInvalidEvent() {
        MerchantVerificationEvent invalid = MerchantVerificationEvent.builder()
                .eventId("")
                .schemaVersion(1)
                .build();

        assertThatThrownBy(() -> handler.deliver(invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
