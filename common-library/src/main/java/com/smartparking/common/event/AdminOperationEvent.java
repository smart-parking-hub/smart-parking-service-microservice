package com.smartparking.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminOperationEvent extends DomainEvent {
    public static final String TOPIC = "admin-operations.events";
    public static final String WITHDRAWAL_REQUESTED = "WITHDRAWAL_REQUESTED";
    public static final String DISPUTE_OPENED = "DISPUTE_OPENED";
    public static final String LOT_REVIEW_REQUESTED = "PARKING_LOT_REVIEW_REQUESTED";
    @Builder.Default private int schemaVersion = 1;
    private Long targetId;
    private Long actorId;
    private Long submissionVersion;
    private BigDecimal amount;
    private String currency;
}
