package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {
    @Mock BookingRepository repository;
    @Mock ParkingLotClient lots;
    @Mock QueueService queue;
    @Mock OutboxPublisherService events;
    @InjectMocks BookingExpirationScheduler scheduler;

    @Test
    void expiryQueriesUseVietnamTimeOnUtcHost() {
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
            LocalDateTime now = LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            scheduler.expireUnpaidBookings();
            scheduler.expireNoShowBookings();
            verify(repository).findByStatusAndExpiresAtBefore(eq(BookingStatusEnum.PENDING_PAYMENT),
                    argThat(time -> !time.isBefore(now.minusMinutes(3)) && time.isBefore(now.minusMinutes(2))));
            verify(repository).findByStatusAndExpiresAtBefore(eq(BookingStatusEnum.PENDING_CHECKIN),
                    argThat(time -> !time.isBefore(now) && time.isBefore(now.plusMinutes(1))));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
    }

    private Booking candidate() {
        return Booking.builder().id(1L).bookingCode("BK-EXPIRY").parkingLotId(2L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV).status(BookingStatusEnum.PENDING_PAYMENT)
                .expiresAt(LocalDateTime.now().minusMinutes(5)).build();
    }

    @Test
    void releasesUnpaidHoldAfterGracePeriod() {
        Booking booking = candidate();
        when(repository.findByStatusAndExpiresAtBefore(eq(BookingStatusEnum.PENDING_PAYMENT), any()))
                .thenReturn(List.of(booking));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));

        scheduler.expireUnpaidBookings();

        assertEquals(BookingStatusEnum.EXPIRED, booking.getStatus());
        verify(lots).releaseSlot(eq(2L), argThat(request -> request.getBookingId().equals(1L)));
        verify(queue).promoteNextInQueue(2L, VehicleTypeEnum.SEDAN_SUV);
    }

    @Test
    void rechecksGracePeriodAfterLockingBooking() {
        Booking candidate = candidate();
        Booking refreshed = candidate();
        refreshed.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByStatusAndExpiresAtBefore(eq(BookingStatusEnum.PENDING_PAYMENT), any()))
                .thenReturn(List.of(candidate));
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(refreshed));

        scheduler.expireUnpaidBookings();

        assertEquals(BookingStatusEnum.PENDING_PAYMENT, refreshed.getStatus());
        verify(repository, never()).save(any());
        verifyNoInteractions(lots, queue, events);
    }
}
