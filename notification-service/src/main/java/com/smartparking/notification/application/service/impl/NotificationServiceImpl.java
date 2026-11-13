package com.smartparking.notification.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.application.dto.NotificationDto;
import com.smartparking.notification.application.service.NotificationService;
import com.smartparking.notification.domain.model.Notification;
import com.smartparking.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> getMyNotifications(Long userId, Pageable pageable) {
        Page<NotificationDto> page = notificationRepository.findByUserId(
                        userId, sanitizeNotificationPage(pageable))
                .map(NotificationDto::fromEntity);
        return new PageResponse<>(page);
    }

    private Pageable sanitizeNotificationPage(Pageable pageable) {
        Pageable safe = com.smartparking.common.util.Pageables.sanitize(pageable);
        var direction = safe.getSort().getOrderFor("createdAt") == null
                ? org.springframework.data.domain.Sort.Direction.DESC
                : safe.getSort().getOrderFor("createdAt").getDirection();
        return org.springframework.data.domain.PageRequest.of(safe.getPageNumber(), safe.getPageSize(),
                org.springframework.data.domain.Sort.by(direction, "createdAt", "id"));
    }

    @Override
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Override
    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        int updated = notificationRepository.markAsRead(userId, notificationId);
        log.info("Marked notification {} as read for user {}. Rows={}", notificationId, userId, updated);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsRead(userId);
        log.info("Marked all notifications as read for user {}. Rows={}", userId, updated);
    }

    @Override
    @Transactional
    public NotificationDto createNotification(Long userId, String title, String content,
                                             NotificationTypeEnum type, String channel,
                                             String targetType, Long targetId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .content(content)
                .type(type != null ? type : NotificationTypeEnum.SYSTEM)
                .channel(channel != null ? channel : "IN_APP")
                .targetType(targetType)
                .targetId(targetId)
                .status("SENT")
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification {} for user {}: {}", saved.getId(), userId, title);
        return NotificationDto.fromEntity(saved);
    }
}
