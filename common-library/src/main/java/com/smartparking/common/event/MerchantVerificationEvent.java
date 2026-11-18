package com.smartparking.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.Instant;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MerchantVerificationEvent extends DomainEvent {
    public static final String TOPIC = "merchant-verification.events";
    public static final String APPROVED = "MERCHANT_VERIFICATION_APPROVED";
    public static final String CHANGES_REQUESTED = "MERCHANT_VERIFICATION_CHANGES_REQUESTED";

    @Builder.Default
    private int schemaVersion = 1;
    private String type;
    private Long merchantUserId;
    private Long dossierId;
    private Integer submissionVersion;
    private Instant occurredAt;
}
