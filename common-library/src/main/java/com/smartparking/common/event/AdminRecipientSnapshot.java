package com.smartparking.common.event;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AdminRecipientSnapshot extends DomainEvent {
    public static final String TOPIC = "admin-recipient-snapshots.events";
    public static final String TYPE = "ADMIN_RECIPIENT_SNAPSHOT";
    @Builder.Default private int schemaVersion = 1;
    private long snapshotVersion;
    private List<Long> recipientIds;
}
