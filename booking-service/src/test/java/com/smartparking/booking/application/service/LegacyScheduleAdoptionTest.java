package com.smartparking.booking.application.service;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.common.enums.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class LegacyScheduleAdoptionTest {
    @Test void oldHoldIsActivatedOnlyAfterTheOwningBookingCommitsWithoutChangingItsTimes() {
        var repository=mock(BookingRepository.class);var lots=mock(ParkingLotClient.class);
        var service=new LegacyScheduleAdoption(repository,lots);ReflectionTestUtils.setField(service,"earlyMinutes",30);
        var start=LocalDateTime.of(2026,11,20,13,10);var end=start.plusHours(2);
        var booking=Booking.builder().id(1L).parkingLotId(2L).vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.PENDING_CHECKIN).expectedCheckInTime(start).expectedCheckOutTime(end).expiresAt(start.plusHours(1)).build();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertTrue(service.adopt(1L));assertEquals(0,booking.getSchedulePolicyVersion());
            assertEquals(start,booking.getExpectedCheckInTime());assertEquals(end,booking.getExpectedCheckOutTime());
            verify(lots).prepareLegacyImport(eq(2L),argThat(snapshot->snapshot.earlyMinutes()==30&&snapshot.startTime().equals(start)));
            verify(lots,never()).activateLegacyImport(anyLong(),anyLong());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(lots).activateLegacyImport(2L,1L);
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }
}
