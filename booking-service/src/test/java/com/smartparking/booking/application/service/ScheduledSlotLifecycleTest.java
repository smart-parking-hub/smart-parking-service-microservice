package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ScheduledSlotLifecycleTest {
    private final ParkingLotClient client=mock(ParkingLotClient.class);
    private final Booking booking=Booking.builder().id(10L).parkingLotId(20L).schedulePolicyVersion(1)
            .checkOutTime(LocalDateTime.of(2026,11,20,12,0)).build();
    @BeforeEach void begin(){TransactionSynchronizationManager.initSynchronization();}
    @AfterEach void end(){TransactionSynchronizationManager.clearSynchronization();}
    @Test void rollbackCompensatesOnlyItsOccupationAttempt() {
        ScheduledSlotLifecycle.occupy(booking,client);
        var synchronizations=TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(client).occupyReservation(eq(20L),eq(10L),argThat(body->body.get("token")!=null));
        verify(client).abortOccupation(eq(20L),eq(10L),argThat(body->body.get("token")!=null));
    }
    @Test void checkoutIsNotDeliveredBeforeCommitOrOnRollback() {
        ScheduledSlotLifecycle.checkoutAfterCommit(booking,client);verifyNoInteractions(client);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(client);
    }
    @Test void cancellationCannotFreeAPlaceUntilItsBookingCommitSucceeds() {
        var queue=mock(QueueService.class);
        ScheduledSlotLifecycle.releaseAfterCommit(booking,client,queue);
        verifyNoInteractions(client,queue);
        TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(client,queue);
    }
    @Test void committedCheckoutIsDeliveredAndRemoteFailureIsLeftForReconciliation() {
        when(client.completeReservation(anyLong(),anyLong(),anyMap())).thenThrow(new IllegalStateException("Temporary failure"));
        ScheduledSlotLifecycle.checkoutAfterCommit(booking,client);
        Assertions.assertDoesNotThrow(()->TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit));
        verify(client).completeReservation(eq(20L),eq(10L),argThat(body->booking.getCheckOutTime().equals(body.get("checkoutTime"))));
    }
}
