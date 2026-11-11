package com.smartparking.identity.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWarningItemResponse {
    private Long id;
    private String reason;
    private String penaltyType;
    private String evidenceUrl;
    private Long issuedByAdminId;
    private String adminName;
    private LocalDateTime createdAt;
}

