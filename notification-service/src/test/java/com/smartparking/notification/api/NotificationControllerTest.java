package com.smartparking.notification.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.notification.api.controller.NotificationController;
import com.smartparking.notification.application.dto.NotificationDto;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "user01", "DRIVER");
    }

    @Test
    @DisplayName("getMyNotifications returns paginated notifications")
    void testGetMyNotifications() {
        PageResponse<NotificationDto> page = new PageResponse<>(new PageImpl<>(List.of(NotificationDto.builder().id(1L).build())));
        when(notificationService.getMyNotifications(eq(100L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<NotificationDto>>> response =
                notificationController.getMyNotifications(user, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getUnreadCount returns unread notifications count")
    void testGetUnreadCount() {
        when(notificationService.getUnreadCount(100L)).thenReturn(5);

        ResponseEntity<ApiResponse<Integer>> response = notificationController.getUnreadCount(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(5, response.getBody().getData());
    }

    @Test
    @DisplayName("markAsRead delegates to notificationService")
    void testMarkAsRead() {
        ResponseEntity<ApiResponse<Void>> response = notificationController.markAsRead(user, 10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService, times(1)).markAsRead(100L, 10L);
    }

    @Test
    @DisplayName("markAllAsRead delegates to notificationService")
    void testMarkAllAsRead() {
        ResponseEntity<ApiResponse<Void>> response = notificationController.markAllAsRead(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService, times(1)).markAllAsRead(100L);
    }
}
