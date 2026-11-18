package com.smartparking.notification.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.domain.model.ProcessedEvent;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private UserEventListener listener;

    @Test
    @DisplayName("Duplicate user event is ignored")
    void testDuplicateUserEventIgnored() {
        when(processedEventRepository.existsByEventId("usr-evt-1")).thenReturn(true);

        String payload = "{\"eventId\":\"usr-evt-1\",\"eventType\":\"USER_WARNED\",\"userId\":10,\"warningId\":1}";
        listener.handleUserEvent(payload);

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handles USER_WARNED event when user is not suspended")
    void testUserWarnedNotSuspended() {
        when(processedEventRepository.existsByEventId("usr-evt-2")).thenReturn(false);

        String payload = "{\"eventId\":\"usr-evt-2\",\"eventType\":\"USER_WARNED\",\"userId\":10,\"warningId\":1,\"reason\":\"Bùng cọc\",\"warningCount\":1,\"isSuspended\":false}";
        listener.handleUserEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L),
                eq("Cảnh báo vi phạm tài khoản (1/3)"),
                contains("Bùng cọc"),
                eq(NotificationTypeEnum.SYSTEM),
                eq("IN_APP"),
                eq("USER_WARNING"),
                eq(1L)
        );
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Handles USER_WARNED event when user is suspended")
    void testUserWarnedSuspended() {
        when(processedEventRepository.existsByEventId("usr-evt-3")).thenReturn(false);

        String payload = "{\"eventId\":\"usr-evt-3\",\"eventType\":\"USER_WARNED\",\"userId\":10,\"warningId\":3,\"reason\":\"Vi phạm lần 3\",\"warningCount\":3,\"isSuspended\":true}";
        listener.handleUserEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L),
                eq("Tài khoản đã bị tạm khóa 7 ngày"),
                contains("tạm khóa 7 ngày"),
                eq(NotificationTypeEnum.SYSTEM),
                eq("IN_APP"),
                eq("USER_WARNING"),
                eq(3L)
        );
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Malformed JSON reaches the dead-letter handler")
    void testMalformedJsonPayload() {
        assertThrows(IllegalArgumentException.class, () -> listener.handleUserEvent("not-a-valid-json"));
    }
}

