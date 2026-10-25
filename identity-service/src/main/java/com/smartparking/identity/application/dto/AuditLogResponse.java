package com.smartparking.identity.application.dto;

import com.smartparking.identity.domain.model.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    private Long id;
    private Long actorUserId;
    private String action;
    private String targetType;
    private Long targetId;
    private LocalDateTime createdAt;
    private String detail;
    private String result;

    public static AuditLogResponse from(AuditLog log) {
        return AuditLogResponse.builder()
                .id(log.getId())
                .actorUserId(log.getActorUserId())
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .createdAt(log.getCreatedAt())
                .detail(log.getDetail())
                .result(log.getResult())
                .build();
    }
}
