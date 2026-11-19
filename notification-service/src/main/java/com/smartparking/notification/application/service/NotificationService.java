package com.smartparking.notification.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.dto.NotificationDto;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    PageResponse<NotificationDto> getMyNotifications(Long userId, Pageable pageable);
    int getUnreadCount(Long userId);
    void markAsRead(Long userId, Long notificationId);
    void markAllAsRead(Long userId);
    NotificationDto createNotification(Long userId, String title, String content, NotificationTypeEnum type, String channel, String targetType, Long targetId);
    NotificationDto createNotification(Long userId, String title, String content, NotificationTypeEnum type, String channel, String targetType, Long targetId, String sourceEventId);
    boolean notificationExists(Long userId, String sourceEventId);
}
