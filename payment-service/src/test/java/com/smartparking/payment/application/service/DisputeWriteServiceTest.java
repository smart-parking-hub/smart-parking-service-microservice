package com.smartparking.payment.application.service;

import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.event.AdminOperationEvent;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.messaging.TransactionalEventOutbox;
import com.smartparking.payment.application.service.impl.DisputeWriteService;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.repository.DisputeEvidenceFileRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisputeWriteServiceTest {
    @Mock DisputeRepository disputes;
    @Mock DisputeEvidenceFileRepository evidence;
    @Mock TransactionalEventOutbox outbox;
    @InjectMocks DisputeWriteService service;

    private Dispute saved() {
        return Dispute.builder().id(55L).bookingId(10L).driverId(101L).merchantId(202L)
                .parkingLotId(303L).reason("Sai phí").status(DisputeStatusEnum.PENDING).build();
    }

    @Test void writesTheAdminEventInTheDisputeRecordTransaction() {
        when(disputes.findByBookingId(10L)).thenReturn(Optional.empty());
        when(disputes.saveAndFlush(any())).thenReturn(saved());
        var result = service.createDisputeRecord(101L, 10L, 202L, 303L, "Sai phí", "Mô tả", List.of());
        assertEquals(55L, result.getId());
        verify(outbox).enqueue(eq(AdminOperationEvent.TOPIC), eq("dispute:55"), argThat(event ->
                event instanceof AdminOperationEvent operation
                        && AdminOperationEvent.DISPUTE_OPENED.equals(operation.getEventType())
                        && Long.valueOf(55).equals(operation.getTargetId())
                        && Long.valueOf(101).equals(operation.getActorId())));
    }

    @Test void duplicateBookingDoesNotPublishAnotherEvent() {
        when(disputes.findByBookingId(10L)).thenReturn(Optional.of(saved()));
        assertThrows(AppException.class, () -> service.createDisputeRecord(101L, 10L, 202L, 303L, "Sai phí", null, List.of()));
        verifyNoInteractions(outbox);
        verify(disputes, never()).saveAndFlush(any());
    }

    @Test void anOutboxFailurePropagatesToTheTransactionalCaller() {
        when(disputes.findByBookingId(10L)).thenReturn(Optional.empty());
        when(disputes.saveAndFlush(any())).thenReturn(saved());
        doThrow(new IllegalStateException("Outbox unavailable")).when(outbox).enqueue(any(), any(), any());
        assertThrows(IllegalStateException.class, () ->
                service.createDisputeRecord(101L, 10L, 202L, 303L, "Sai phí", null, List.of()));
    }
}
