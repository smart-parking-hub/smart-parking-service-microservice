package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.RoleEnum;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.domain.model.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailResponse {
    private Long id;
    private String phone;
    private String email;
    private String fullName;
    private RoleEnum role;
    private UserStatusEnum status;
    private String statusReason;
    private Integer warningCount;
    private LocalDateTime suspendedUntil;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long bookingCount;
    private long disputeCount;

    public static AdminUserDetailResponse fromEntity(User u, long bookings, long disputes) {
        return builder()
                .id(u.getId())
                .phone(u.getPhone())
                .email(u.getEmail())
                .fullName(u.getFullName())
                .role(u.getRole())
                .status(u.getStatus())
                .statusReason(u.getStatusReason())
                .warningCount(u.getWarningCount())
                .suspendedUntil(u.getSuspendedUntil())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .bookingCount(bookings)
                .disputeCount(disputes)
                .build();
    }
}
