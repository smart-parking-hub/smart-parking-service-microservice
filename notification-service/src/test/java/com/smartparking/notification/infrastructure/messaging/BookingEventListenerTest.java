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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BookingEventListener listener;

    @Test
    @DisplayName("Duplicate event is ignored")
    void testDuplicateEventIgnored() {
        when(processedEventRepository.existsByEventId("evt-123")).thenReturn(true);

        String payload = "{\"eventId\":\"evt-123\",\"eventType\":\"BOOKING_CREATED\",\"userId\":10,\"bookingId\":1}";
        listener.handleBookingEvent(payload);

        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handles BOOKING_CREATED event")
    void testBookingCreated() {
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);

        String payload = "{\"eventId\":\"evt-1\",\"eventType\":\"BOOKING_CREATED\",\"userId\":10,\"bookingId\":50,\"licensePlate\":\"51A-12345\"}";
        listener.handleBookingEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Đặt chỗ thành công"), contains("51A-12345"), eq(NotificationTypeEnum.BOOKING), eq("IN_APP"), eq("BOOKING"), eq(50L)
        );
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }

    @Test
    @DisplayName("Handles BOOKING_CHECKED_IN event")
    void testBookingCheckedIn() {
        when(processedEventRepository.existsByEventId("evt-2")).thenReturn(false);

        String payload = "{\"eventId\":\"evt-2\",\"eventType\":\"BOOKING_CHECKED_IN\",\"userId\":10,\"bookingId\":50}";
        listener.handleBookingEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Check-in thành công"), anyString(), eq(NotificationTypeEnum.BOOKING), eq("IN_APP"), eq("BOOKING"), eq(50L)
        );
    }

    @Test
    @DisplayName("Handles BOOKING_COMPLETED event")
    void testBookingCompleted() {
        when(processedEventRepository.existsByEventId("evt-3")).thenReturn(false);

        String payload = "{\"eventId\":\"evt-3\",\"eventType\":\"BOOKING_COMPLETED\",\"userId\":10,\"bookingId\":50}";
        listener.handleBookingEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Hoàn tất đỗ xe"), anyString(), eq(NotificationTypeEnum.BOOKING), eq("IN_APP"), eq("BOOKING"), eq(50L)
        );
    }

    @Test
    @DisplayName("Handles BOOKING_CANCELLED event")
    void testBookingCancelled() {
        when(processedEventRepository.existsByEventId("evt-4")).thenReturn(false);

        String payload = "{\"eventId\":\"evt-4\",\"eventType\":\"BOOKING_CANCELLED\",\"userId\":10,\"bookingId\":50,\"reason\":\"Driver request\"}";
        listener.handleBookingEvent(payload);

        verify(notificationService, times(1)).createNotification(
                eq(10L), eq("Đặt chỗ đã hủy"), contains("Driver request"), eq(NotificationTypeEnum.BOOKING), eq("IN_APP"), eq("BOOKING"), eq(50L)
        );
    }

    @Test
    @DisplayName("Handles unhandled event type or missing userId gracefully")
    void testUnhandledEventTypeAndMissingUserId() {
        // Unknown type
        listener.handleBookingEvent("{\"eventId\":\"evt-5\",\"eventType\":\"UNKNOWN_TYPE\",\"userId\":10}");
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());

        // Missing userId
        listener.handleBookingEvent("{\"eventId\":\"evt-6\",\"eventType\":\"BOOKING_CREATED\"}");
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Handles malformed json payload safely without throwing")
    void testMalformedJsonPayload() {
        assertDoesNotThrow(() -> listener.handleBookingEvent("not-valid-json"));
    }
}
