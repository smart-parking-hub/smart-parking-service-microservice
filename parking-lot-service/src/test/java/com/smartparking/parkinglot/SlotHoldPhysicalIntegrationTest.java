package com.smartparking.parkinglot;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.SlotHoldResponse;
import com.smartparking.parkinglot.application.service.impl.SlotServiceImpl;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlotHoldPhysicalIntegrationTest {

    @Mock
    private SlotRepository slotRepository;
    @Mock
    private PricingRuleRepository pricingRuleRepository;
    @Mock
    private ParkingLotRepository parkingLotRepository;
    @Mock
    private SlotHoldRepository slotHoldRepository;
    @Mock
    private ParkingLayoutRepository parkingLayoutRepository;
    @Mock
    private PhysicalParkingSlotRepository physicalParkingSlotRepository;
    @Mock
    private CurrentSlotAllocationRepository currentSlotAllocationRepository;

    @InjectMocks
    private SlotServiceImpl slotService;

    private final Long parkingLotId = 1L;
    private final Long merchantId = 10L;
    private ParkingLot lot;
    private Slot slot;
    private PricingRule price;
    private PhysicalParkingSlot physSlot;

    @BeforeEach
    void setUp() {
        lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantId)
                .name("Central Park")
                .status(ParkingLotStatusEnum.APPROVED)
                .build();

        slot = Slot.builder()
                .id(100L)
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(10)
                .availableCapacity(10)
                .build();

        price = PricingRule.builder()
                .id(200L)
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("25000"))
                .build();

        physSlot = PhysicalParkingSlot.builder()
                .id("501")
                .parkingLotId(parkingLotId)
                .slotGroupId(100L)
                .code("P-01")
                .normalizedCode("p-01")
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status("AVAILABLE")
                .build();
    }

    @Test
    @DisplayName("AC5: Hold with physicalSlotId successfully holds slot and creates physical allocation")
    void testHoldPhysicalSlotSuccess() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));
        when(slotHoldRepository.findById(12345L)).thenReturn(Optional.empty());

        ParkingLayout layout = ParkingLayout.builder()
                .activePublicationId("pub-99")
                .allocationMode(AllocationMode.PHYSICAL)
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        when(physicalParkingSlotRepository.findByIdAndParkingLotIdForUpdate("501", parkingLotId))
                .thenReturn(Optional.of(physSlot));
        when(currentSlotAllocationRepository.findByPhysicalSlotIdForUpdate("501")).thenReturn(Optional.empty());

        SlotHoldResponse resp = slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12345L, "501");

        assertTrue(resp.isSuccess());
        assertEquals("501", resp.getPhysicalSlotId());
        assertEquals("P-01", resp.getPhysicalSlotCode());
        assertEquals("pub-99", resp.getLayoutPublicationId());
        assertEquals(9, resp.getRemainingCapacity());

        // Verify slot capacity was decremented
        assertEquals(9, slot.getAvailableCapacity());
        verify(slotRepository).save(slot);

        // Verify allocation was saved
        verify(currentSlotAllocationRepository).save(argThat(alloc ->
                "501".equals(alloc.getPhysicalSlotId()) && alloc.getBookingId().equals(12345L)));

        // Verify slot hold was saved
        verify(slotHoldRepository).save(argThat(hold ->
                hold.getBookingId().equals(12345L) && "501".equals(hold.getPhysicalSlotId())));
    }

    @Test
    @DisplayName("AC5: Hold physicalSlotId fails when another booking already holds it, capacity untouched")
    void testHoldPhysicalSlotAlreadyAllocated() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));
        when(slotHoldRepository.findById(12346L)).thenReturn(Optional.empty());

        ParkingLayout layout = ParkingLayout.builder()
                .activePublicationId("pub-99")
                .allocationMode(AllocationMode.PHYSICAL)
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        when(physicalParkingSlotRepository.findByIdAndParkingLotIdForUpdate("501", parkingLotId))
                .thenReturn(Optional.of(physSlot));

        // Physical slot already held by booking 12345
        CurrentSlotAllocation existingAlloc = CurrentSlotAllocation.builder()
                .physicalSlotId("501")
                .bookingId(12345L)
                .build();
        when(currentSlotAllocationRepository.findByPhysicalSlotIdForUpdate("501"))
                .thenReturn(Optional.of(existingAlloc));

        SlotHoldResponse resp = slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12346L, "501");

        assertFalse(resp.isSuccess());
        assertTrue(resp.getMessage().contains("đã có người giữ chỗ"));
        assertEquals(10, slot.getAvailableCapacity()); // Capacity NOT decremented
        verify(slotRepository, never()).save(any());
        verify(currentSlotAllocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("R5: Hold physicalSlotId fails with BAD_REQUEST when allocationMode is GROUP")
    void testHoldPhysicalSlotFailsWhenModeIsGroup() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));

        ParkingLayout layout = ParkingLayout.builder()
                .activePublicationId("pub-99")
                .allocationMode(AllocationMode.GROUP)
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        assertThrows(AppException.class, () ->
                slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12346L, "501"));
    }

    @Test
    @DisplayName("AC5: Auto-allocation in PHYSICAL mode picks first available physical slot")
    void testAutoAllocationInPhysicalMode() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));
        when(slotHoldRepository.findById(12347L)).thenReturn(Optional.empty());

        ParkingLayout layout = ParkingLayout.builder()
                .activePublicationId("pub-99")
                .allocationMode(AllocationMode.PHYSICAL)
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        when(physicalParkingSlotRepository.findAvailableSlotsForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(List.of(physSlot));

        // Hold called with physicalSlotId = null
        SlotHoldResponse resp = slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12347L, null);

        assertTrue(resp.isSuccess());
        assertEquals("501", resp.getPhysicalSlotId());
        assertEquals("P-01", resp.getPhysicalSlotCode());
        assertEquals(9, resp.getRemainingCapacity());
        verify(currentSlotAllocationRepository).save(any(CurrentSlotAllocation.class));
    }

    @Test
    @DisplayName("AC5: GROUP mode hold without physicalSlotId does not assign physical slot (backward compatible)")
    void testGroupModeHoldWithoutPhysicalSlot() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));
        when(slotHoldRepository.findById(12348L)).thenReturn(Optional.empty());

        ParkingLayout layout = ParkingLayout.builder()
                .activePublicationId("pub-99")
                .allocationMode(AllocationMode.GROUP)
                .build();
        when(parkingLayoutRepository.findByParkingLotId(parkingLotId)).thenReturn(Optional.of(layout));

        SlotHoldResponse resp = slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12348L, null);

        assertTrue(resp.isSuccess());
        assertNull(resp.getPhysicalSlotId());
        assertNull(resp.getPhysicalSlotCode());
        assertEquals(9, resp.getRemainingCapacity());
        verify(currentSlotAllocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC6: Retry holdSlot with same bookingId returns previous hold idempotently")
    void testHoldSlotRetryIdempotent() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));

        SlotHold previousHold = SlotHold.builder()
                .bookingId(12349L)
                .slotId(slot.getId())
                .released(false)
                .physicalSlotId("501")
                .publicationId("pub-99")
                .build();
        when(slotHoldRepository.findById(12349L)).thenReturn(Optional.of(previousHold));
        when(physicalParkingSlotRepository.findById("501")).thenReturn(Optional.of(physSlot));

        SlotHoldResponse resp = slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12349L, "501");

        assertTrue(resp.isSuccess());
        assertEquals("501", resp.getPhysicalSlotId());
        assertEquals("P-01", resp.getPhysicalSlotCode());
        // Verify capacity was NOT decremented again
        verify(slotRepository, never()).save(slot);
    }

    @Test
    @DisplayName("AC6: Retry holdSlot with same bookingId but different physicalSlotId throws CONFLICT (409)")
    void testHoldSlotRetryChangedPhysicalSlotConflict() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(price));

        SlotHold previousHold = SlotHold.builder()
                .bookingId(12350L)
                .slotId(slot.getId())
                .released(false)
                .physicalSlotId("501")
                .publicationId("pub-99")
                .build();
        when(slotHoldRepository.findById(12350L)).thenReturn(Optional.of(previousHold));

        AppException ex = assertThrows(AppException.class, () ->
                slotService.holdSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12350L, "502"));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC7: releaseSlot removes physical allocation, marks hold released, increments capacity")
    void testReleaseSlotCleansAllocation() {
        slot.setAvailableCapacity(8);
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));

        SlotHold hold = SlotHold.builder()
                .bookingId(12351L)
                .slotId(slot.getId())
                .released(false)
                .physicalSlotId("501")
                .build();
        when(slotHoldRepository.findById(12351L)).thenReturn(Optional.of(hold));

        slotService.releaseSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12351L);

        assertTrue(hold.isReleased());
        assertEquals(9, slot.getAvailableCapacity());
        verify(currentSlotAllocationRepository).deleteByBookingId(12351L);
        verify(slotRepository).save(slot);
        verify(slotHoldRepository).save(hold);
    }

    @Test
    @DisplayName("AC7: releaseSlot is idempotent when hold was already released")
    void testReleaseSlotIdempotent() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));

        SlotHold hold = SlotHold.builder()
                .bookingId(12352L)
                .slotId(slot.getId())
                .released(true) // already released
                .build();
        when(slotHoldRepository.findById(12352L)).thenReturn(Optional.of(hold));

        slotService.releaseSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 12352L);

        // Does not alter capacity or call deleteByBookingId
        verify(currentSlotAllocationRepository, never()).deleteByBookingId(any());
        verify(slotRepository, never()).save(any());
    }
}
