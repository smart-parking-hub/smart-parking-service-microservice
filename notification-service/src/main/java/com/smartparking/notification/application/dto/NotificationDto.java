package com.smartparking.notification.application.dto;

import com.smartparking.common.enums.NotificationTypeEnum;
import com.smartparking.notification.domain.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String channel;
    private NotificationTypeEnum type;
    private String targetType;
    private Long targetId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private String status;

    public static NotificationDto fromEntity(Notification entity) {
        if (entity == null) return null;
        return NotificationDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .content(entity.getContent())
                .channel(entity.getChannel())
                .type(entity.getType())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .readAt(entity.getReadAt())
                .createdAt(entity.getCreatedAt())
                .sentAt(entity.getSentAt())
                .status(entity.getStatus())
                .build();
    }
}
