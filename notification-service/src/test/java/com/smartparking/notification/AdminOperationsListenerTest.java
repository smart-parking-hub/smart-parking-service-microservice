package com.smartparking.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.event.AdminRecipientSnapshot;
import com.smartparking.notification.application.service.AdminOperationsHandler;
import com.smartparking.notification.infrastructure.messaging.AdminOperationsListener;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminOperationsListenerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AdminOperationsHandler handler = mock(AdminOperationsHandler.class);
    private final AdminOperationsListener listener = new AdminOperationsListener(mapper, handler);

    @Test void missingEnvelopeFieldsCannotBeReplacedWithProducerDefaultsOnReplay() throws Exception {
        var event = AdminOperationEvent.builder().eventType(AdminOperationEvent.DISPUTE_OPENED)
                .targetId(55L).actorId(101L).build();
        for (String field : List.of("eventId", "timestamp", "eventType", "schemaVersion")) {
            ObjectNode json = mapper.valueToTree(event);
            json.remove(field);
            assertThatThrownBy(() -> listener.operations(json.toString())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> listener.operations("{invalid-json")).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(handler);
    }

    @Test void validOperationsAndSnapshotsPreserveTheOriginalEnvelope() throws Exception {
        var event = AdminOperationEvent.builder().eventType(AdminOperationEvent.DISPUTE_OPENED)
                .targetId(55L).actorId(101L).build();
        listener.operations(mapper.writeValueAsString(event));
        verify(handler).deliver(argThat(received -> received.getEventId().equals(event.getEventId())
                && received.getTimestamp().equals(event.getTimestamp())));
        var snapshot = AdminRecipientSnapshot.builder().eventType(AdminRecipientSnapshot.TYPE)
                .snapshotVersion(1).recipientIds(List.of(101L)).build();
        listener.recipients(mapper.writeValueAsString(snapshot));
        verify(handler).applySnapshot(argThat(received -> received.getEventId().equals(snapshot.getEventId())
                && received.getRecipientIds().equals(List.of(101L))));
    }
}
