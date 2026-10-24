package com.smartparking.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.dto.NotificationDto;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.domain.repository.ProcessedEventRepository;
import com.smartparking.notification.infrastructure.messaging.BookingEventListener;
import com.smartparking.notification.infrastructure.messaging.PaymentEventListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private BookingEventListener bookingEventListener;

    @Autowired
    private PaymentEventListener paymentEventListener;

    @Autowired
    private ObjectMapper objectMapper;

    private final Long testUserId = 888L;

    @Test
    @DisplayName("Create, count unread, and mark notifications as read")
    void testNotificationLifecycle() {
        NotificationDto n1 = notificationService.createNotification(
                testUserId, "Test Title 1", "Content 1", NotificationTypeEnum.SYSTEM, "IN_APP", "BOOKING", 1L);
        NotificationDto n2 = notificationService.createNotification(
                testUserId, "Test Title 2", "Content 2", NotificationTypeEnum.BOOKING, "IN_APP", "BOOKING", 2L);

        assertNotNull(n1.getId());
        assertNotNull(n2.getId());

        assertEquals(2, notificationService.getUnreadCount(testUserId));

        PageResponse<NotificationDto> page = notificationService.getMyNotifications(testUserId, PageRequest.of(0, 10));
        assertEquals(2, page.getContent().size());

        notificationService.markAsRead(testUserId, n1.getId());
        assertEquals(1, notificationService.getUnreadCount(testUserId));

        notificationService.markAllAsRead(testUserId);
        assertEquals(0, notificationService.getUnreadCount(testUserId));
    }

    @Test
    @DisplayName("BookingEventListener consumes BOOKING_CREATED with idempotency deduplication")
    void testBookingEventListenerIdempotency() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "BOOKING_CREATED",
                "bookingId", 1234L,
                "userId", testUserId,
                "licensePlate", "29C-12345"
        );
        String jsonPayload = objectMapper.writeValueAsString(event);

        // First delivery
        bookingEventListener.handleBookingEvent(jsonPayload);
        assertTrue(processedEventRepository.existsByEventId(eventId));
        assertEquals(1, notificationService.getUnreadCount(testUserId));

        // Duplicate delivery with same eventId
        bookingEventListener.handleBookingEvent(jsonPayload);
        assertEquals(1, notificationService.getUnreadCount(testUserId)); // Still 1, not duplicated!
    }

    @Test
    @DisplayName("PaymentEventListener consumes PAYMENT_COMPLETED with idempotency deduplication")
    void testPaymentEventListenerIdempotency() throws Exception {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> event = Map.of(
                "eventId", eventId,
                "eventType", "PAYMENT_COMPLETED",
                "paymentId", 555L,
                "bookingId", 1234L,
                "userId", testUserId,
                "amount", "50000"
        );
        String jsonPayload = objectMapper.writeValueAsString(event);

        // First delivery
        paymentEventListener.handlePaymentEvent(jsonPayload);
        assertTrue(processedEventRepository.existsByEventId(eventId));
        assertEquals(1, notificationService.getUnreadCount(testUserId));

        // Duplicate delivery with same eventId
        paymentEventListener.handlePaymentEvent(jsonPayload);
        assertEquals(1, notificationService.getUnreadCount(testUserId));
    }
}
