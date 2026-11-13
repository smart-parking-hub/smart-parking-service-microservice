package com.smartparking.notification.domain.model;

import com.smartparking.common.enums.NotificationTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "uq_notification_source_recipient", columnNames = {"source_event_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String channel = "IN_APP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private NotificationTypeEnum type = NotificationTypeEnum.SYSTEM;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "source_event_id", length = 100)
    private String sourceEventId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "SENT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
