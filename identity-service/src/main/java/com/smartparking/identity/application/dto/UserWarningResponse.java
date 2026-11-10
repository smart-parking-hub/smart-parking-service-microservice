package com.smartparking.identity.application.dto;

import com.smartparking.common.enums.UserStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWarningResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private String reason;
    private String penaltyType;
    private String evidenceUrl;
    private Integer warningCount;
    private UserStatusEnum userStatus;
    private LocalDateTime suspendedUntil;
    private Long issuedByAdminId;
    private String adminName;
    private LocalDateTime createdAt;
}

