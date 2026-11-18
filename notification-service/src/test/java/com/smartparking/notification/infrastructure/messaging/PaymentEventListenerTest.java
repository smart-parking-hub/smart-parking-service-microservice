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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PaymentEventListener listener;

    @Test
    @DisplayName("Duplicate payment event is ignored")
    void testDuplicatePaymentEventIgnored() {
        when(processedEventRepository.existsByEventId("pay-evt-1")).thenReturn(true);

        String payload = "{\"eventId\":\"pay-evt-1\",\"eventType\":\"PAYMENT_COMPLETED\",\"userId\":10,\"bookingId\":1}";
        listener.handlePaymentEvent(payload);

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handles PAYMENT_COMPLETED event for generic payment")
    void testPaymentCompleted() {
        when(processedEventRepository.existsByEventId("pay-evt-2")).thenReturn(false);

        String payload = "{\"eventId\":\"pay-evt-2\",\"eventType\":\"PAYMENT_COMPLETED\",\"userId\":10,\"bookingId\":50,\"amount\":\"50000\"}";
        listener.handlePaymentEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Thanh toán thành công"), contains("50000"), eq(NotificationTypeEnum.PAYMENT), eq("IN_APP"), eq("PAYMENT"), eq(50L)
        );
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Handles PAYMENT_COMPLETED event for DEPOSIT with booking confirmation and QR ready notification")
    void testPaymentCompletedDeposit() {
        when(processedEventRepository.existsByEventId("pay-evt-dep-1")).thenReturn(false);

        String payload = "{\"eventId\":\"pay-evt-dep-1\",\"eventType\":\"PAYMENT_COMPLETED\",\"paymentType\":\"DEPOSIT\",\"userId\":10,\"bookingId\":50,\"amount\":\"20000\"}";
        listener.handlePaymentEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Đặt chỗ thành công"), contains("Mã QR vào bãi đã sẵn sàng"), eq(NotificationTypeEnum.BOOKING), eq("IN_APP"), eq("BOOKING"), eq(50L)
        );
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Handles PAYMENT_FAILED event")
    void testPaymentFailed() {
        when(processedEventRepository.existsByEventId("pay-evt-3")).thenReturn(false);

        String payload = "{\"eventId\":\"pay-evt-3\",\"eventType\":\"PAYMENT_FAILED\",\"userId\":10,\"bookingId\":50,\"reason\":\"Card declined\"}";
        listener.handlePaymentEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Thanh toán thất bại"), contains("Card declined"), eq(NotificationTypeEnum.PAYMENT), eq("IN_APP"), eq("PAYMENT"), eq(50L)
        );
    }

    @Test
    @DisplayName("Handles unhandled event type or missing userId gracefully")
    void testUnhandledEventTypeAndMissingUserId() {
        // Unknown type
        listener.handlePaymentEvent("{\"eventId\":\"pay-evt-4\",\"eventType\":\"UNKNOWN_PAYMENT\",\"userId\":10}");
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());

        // Missing userId
        listener.handlePaymentEvent("{\"eventId\":\"pay-evt-5\",\"eventType\":\"PAYMENT_COMPLETED\"}");
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handles PAYMENT_FAILED default reason when reason is missing")
    void testPaymentFailedDefaultReason() {
        when(processedEventRepository.existsByEventId("pay-evt-6")).thenReturn(false);

        String payload = "{\"eventId\":\"pay-evt-6\",\"eventType\":\"PAYMENT_FAILED\",\"userId\":10,\"bookingId\":50}";
        listener.handlePaymentEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Thanh toán thất bại"), contains("Lỗi hệ thống"), eq(NotificationTypeEnum.PAYMENT), eq("IN_APP"), eq("PAYMENT"), eq(50L)
        );
    }

    @Test
    @DisplayName("Malformed JSON reaches the dead-letter handler")
    void testMalformedJsonPayload() {
        assertThrows(IllegalArgumentException.class, () -> listener.handlePaymentEvent("not-valid-json"));
    }
}
