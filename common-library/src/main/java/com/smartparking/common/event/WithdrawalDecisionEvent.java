package com.smartparking.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class WithdrawalDecisionEvent extends DomainEvent {
    public static final String TOPIC = "withdrawal-decisions.events";
    public static final String APPROVED = "WITHDRAWAL_APPROVED";
    public static final String REJECTED = "WITHDRAWAL_REJECTED";
    @Builder.Default private int schemaVersion = 1;
    private Long withdrawalId;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
}
