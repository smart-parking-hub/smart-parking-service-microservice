package com.smartparking.parkinglot;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.infrastructure.client.BookingHoldVerifier;
import com.smartparking.parkinglot.infrastructure.scheduler.SlotHoldReconciliation;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class SlotHoldReconciliationTest {
 @Mock SlotHoldRepository holds; @Mock SlotRepository slots;
 @Mock SlotService service; @Mock BookingHoldVerifier bookingVerifier;
 @InjectMocks SlotHoldReconciliation scheduler;
 @Test void confirmedOrphanIsReleasedThroughIdempotentService(){
  when(holds.findTop100ByReleasedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(any())).thenReturn(List.of(SlotHold.builder().bookingId(1L).slotId(2L).build()));
  when(bookingVerifier.canRelease(1L)).thenReturn(true);
  when(slots.findById(2L)).thenReturn(Optional.of(Slot.builder().id(2L).parkingLot(ParkingLot.builder().id(3L).build()).vehicleType(VehicleTypeEnum.HEAVY_TRUCK).build()));
  scheduler.reconcile();verify(service).releaseSlot(3L,VehicleTypeEnum.HEAVY_TRUCK,1L);
 }
 @Test void unavailableBookingServiceNeverReleasesActiveOrUnknownHolds(){
  when(holds.findTop100ByReleasedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(any())).thenReturn(List.of(SlotHold.builder().bookingId(1L).slotId(2L).build()));
  when(bookingVerifier.canRelease(1L)).thenThrow(new RuntimeException("unavailable"));
  scheduler.reconcile();verifyNoInteractions(service,slots);
 }
}
