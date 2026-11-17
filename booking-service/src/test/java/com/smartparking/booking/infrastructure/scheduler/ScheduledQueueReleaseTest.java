package com.smartparking.booking.infrastructure.scheduler;

import com.smartparking.booking.application.service.impl.QueueServiceImpl;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.model.Queue;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.domain.repository.QueueRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.QueueStatusEnum;
import static com.smartparking.common.util.BookingSchedulePolicy.ZONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledQueueReleaseTest {
    @Test void failedAvailabilityLookupDoesNotConsumeTheQueueRetry() {
        var bookings=mock(BookingRepository.class);
        var waiting=mock(QueueRepository.class);
        var lots=mock(ParkingLotClient.class);
        var now=LocalDateTime.now(ZONE);
        var booking=Booking.builder().id(1L).parkingLotId(7L).vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckOutTime(now.minusHours(2)).checkOutTime(now.minusHours(1)).build();
        when(bookings.findSchedulesReadyForQueue(any(),anyLong(),any())).thenReturn(List.of(1L));
        when(bookings.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));
        when(waiting.findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(anyLong(),any(),any()))
                .thenReturn(List.of(Queue.builder().id(9L).build()));
        when(lots.available(anyLong(),any(),anyString(),anyString())).thenThrow(new IllegalStateException("offline"));
        new ScheduledQueueRelease(bookings,new QueueServiceImpl(waiting,lots)).releaseAfterBuffer();
        assertNull(booking.getScheduleQueueNotifiedAt(),"An unavailable parking service must leave the promotion retryable");
        verify(bookings,never()).save(any());
    }
    @Test void aLaterScanRetriesAndPromotesAfterTheParkingServiceRecovers() {
        var bookings=mock(BookingRepository.class);var waiting=mock(QueueRepository.class);var lots=mock(ParkingLotClient.class);
        var now=LocalDateTime.now(ZONE);
        var booking=Booking.builder().id(1L).parkingLotId(7L).vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckOutTime(now.minusHours(2)).checkOutTime(now.minusHours(1)).build();
        var entry=Queue.builder().id(9L).status(QueueStatusEnum.WAITING).build();
        when(bookings.findSchedulesReadyForQueue(any(),anyLong(),any())).thenReturn(List.of(1L),List.of(),List.of(1L));
        when(bookings.findByIdForUpdate(1L)).thenReturn(Optional.of(booking));
        when(waiting.findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(anyLong(),any(),any())).thenReturn(List.of(entry));
        when(lots.available(anyLong(),any(),anyString(),anyString())).thenThrow(new IllegalStateException("offline"))
                .thenReturn(ApiResponse.ok(new ParkingLotClient.ReservationAvailability(1)));
        var job=new ScheduledQueueRelease(bookings,new QueueServiceImpl(waiting,lots));
        job.releaseAfterBuffer();assertNull(booking.getScheduleQueueNotifiedAt());
        job.releaseAfterBuffer();job.releaseAfterBuffer();
        assertNotNull(booking.getScheduleQueueNotifiedAt());
        assertEquals(QueueStatusEnum.NOTIFIED,entry.getStatus());
        verify(bookings,times(1)).save(booking);
        var cursor=org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(bookings,times(3)).findSchedulesReadyForQueue(any(),cursor.capture(),any());
        assertEquals(List.of(0L,1L,0L),cursor.getAllValues());
    }
    @Test void anUnavailableLotDoesNotStarveLaterReadyLots() {
        var bookings=mock(BookingRepository.class);var queues=mock(com.smartparking.booking.application.service.QueueService.class);
        var now=LocalDateTime.now(ZONE);
        var blocked=Booking.builder().id(1L).parkingLotId(7L).vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckOutTime(now.minusHours(2)).checkOutTime(now.minusHours(1)).build();
        var ready=Booking.builder().id(2L).parkingLotId(8L).vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckOutTime(now.minusHours(2)).checkOutTime(now.minusHours(1)).build();
        when(bookings.findSchedulesReadyForQueue(any(),anyLong(),any())).thenReturn(List.of(1L),List.of(2L));
        when(bookings.findByIdForUpdate(1L)).thenReturn(Optional.of(blocked));
        when(bookings.findByIdForUpdate(2L)).thenReturn(Optional.of(ready));
        when(queues.tryPromoteNextInQueue(8L,VehicleTypeEnum.CONTAINER)).thenReturn(true);
        var job=new ScheduledQueueRelease(bookings,queues);job.releaseAfterBuffer();job.releaseAfterBuffer();
        assertNull(blocked.getScheduleQueueNotifiedAt());assertNotNull(ready.getScheduleQueueNotifiedAt());
        verify(bookings,never()).save(blocked);verify(bookings).save(ready);
        verify(bookings).findSchedulesReadyForQueue(any(),eq(1L),any());
    }
}
