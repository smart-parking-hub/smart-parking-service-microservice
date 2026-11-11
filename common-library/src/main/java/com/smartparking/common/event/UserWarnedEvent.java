package com.smartparking.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserWarnedEvent extends DomainEvent {
    private Long warningId;
    private Long userId;
    private String userEmail;
    private String userPhone;
    private String userFullName;
    private String reason;
    private String penaltyType;
    private String evidenceUrl;
    private Integer warningCount;
    private Boolean isSuspended;
    private Instant suspendedUntil;
    private Long issuedByAdminId;
}

