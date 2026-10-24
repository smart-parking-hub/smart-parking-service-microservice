package com.smartparking.notification.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.dto.NotificationDto;
import com.smartparking.notification.application.service.impl.NotificationServiceImpl;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private final Long userId = 100L;

    @Test
    @DisplayName("Create notification assigns SYSTEM and IN_APP as defaults when null")
    void testCreateNotificationDefaults() {
        Notification saved = Notification.builder()
                .id(1L)
                .userId(userId)
                .title("Thông báo hệ thống")
                .content("Bảo trì định kỳ lúc 00:00")
                .type(NotificationTypeEnum.SYSTEM)
                .channel("IN_APP")
                .status("SENT")
                .build();
        when(notificationRepository.save(any(Notification.class))).thenReturn(saved);

        NotificationDto dto = notificationService.createNotification(
                userId, "Thông báo hệ thống", "Bảo trì định kỳ lúc 00:00",
                null, null, "SYSTEM", 0L
        );

        assertNotNull(dto);
        assertEquals(NotificationTypeEnum.SYSTEM, dto.getType());
        assertEquals("IN_APP", dto.getChannel());
        assertEquals("SENT", dto.getStatus());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Get unread count returns number of unread notifications")
    void testGetUnreadCount() {
        when(notificationRepository.countByUserIdAndReadAtIsNull(userId)).thenReturn(5);

        int count = notificationService.getUnreadCount(userId);

        assertEquals(5, count);
    }

    @Test
    @DisplayName("Mark as read calls repository markAsRead")
    void testMarkAsRead() {
        when(notificationRepository.markAsRead(userId, 10L)).thenReturn(1);

        notificationService.markAsRead(userId, 10L);

        verify(notificationRepository, times(1)).markAsRead(userId, 10L);
    }

    @Test
    @DisplayName("Mark all as read calls repository markAllAsRead")
    void testMarkAllAsRead() {
        when(notificationRepository.markAllAsRead(userId)).thenReturn(3);

        notificationService.markAllAsRead(userId);

        verify(notificationRepository, times(1)).markAllAsRead(userId);
    }

    @Test
    @DisplayName("Get my notifications returns page of notifications")
    void testGetMyNotifications() {
        Notification n = Notification.builder()
                .id(1L)
                .userId(userId)
                .title("Booking confirmed")
                .content("Slot A1")
                .type(NotificationTypeEnum.BOOKING)
                .channel("IN_APP")
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n), pageable, 1));

        PageResponse<NotificationDto> response = notificationService.getMyNotifications(userId, pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Booking confirmed", response.getContent().get(0).getTitle());
    }
}
