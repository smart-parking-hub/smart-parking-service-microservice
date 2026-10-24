package com.smartparking.parkinglot.application.service;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.impl.SlotServiceImpl;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.PricingRule;
import com.smartparking.parkinglot.domain.model.Slot;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import com.smartparking.parkinglot.domain.repository.PricingRuleRepository;
import com.smartparking.parkinglot.domain.repository.SlotRepository;
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
class SlotServiceImplTest {

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @InjectMocks
    private SlotServiceImpl slotService;

    private final Long merchantUserId = 100L;
    private final Long parkingLotId = 20L;

    @Test
    @DisplayName("Configure slot fails when parking lot is not APPROVED")
    void testConfigureSlotLotNotApproved() {
        ParkingLot lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.PENDING_APPROVAL)
                .build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        SlotConfigRequest request = SlotConfigRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(10)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                slotService.configureSlot(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Admin duyệt"));
    }

    @Test
    @DisplayName("Configure slot fails when reducing capacity below currently occupied spots")
    void testConfigureSlotCapacityBelowOccupied() {
        ParkingLot lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        // Total 10, available 3 -> occupied 7
        Slot existing = Slot.builder()
                .id(1L)
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(10)
                .availableCapacity(3)
                .build();
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(existing));

        // Attempting to set capacity to 5 (less than occupied 7)
        SlotConfigRequest request = SlotConfigRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(5)
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                slotService.configureSlot(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Không thể giảm sức chứa thấp hơn số chỗ đang sử dụng"));
    }

    @Test
    @DisplayName("Configure slot successfully creates new slot configuration")
    void testConfigureSlotCreateSuccess() {
        ParkingLot lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.empty());

        Slot saved = Slot.builder()
                .id(5L)
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .totalCapacity(15)
                .availableCapacity(15)
                .build();
        when(slotRepository.save(any(Slot.class))).thenReturn(saved);

        SlotConfigRequest request = SlotConfigRequest.builder()
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .totalCapacity(15)
                .build();

        SlotDto result = slotService.configureSlot(merchantUserId, parkingLotId, request);

        assertNotNull(result);
        assertEquals(VehicleTypeEnum.CONTAINER, result.getVehicleType());
        assertEquals(15, result.getTotalCapacity());
        assertEquals(15, result.getAvailableCapacity());
    }

    @Test
    @DisplayName("Hold slot returns success false when slot has 0 available capacity")
    void testHoldSlotFull() {
        Slot fullSlot = Slot.builder()
                .id(1L)
                .parkingLot(ParkingLot.builder().id(parkingLotId).build())
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .totalCapacity(5)
                .availableCapacity(0)
                .build();
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.of(fullSlot));

        SlotHoldResponse response = slotService.holdSlot(parkingLotId, VehicleTypeEnum.CONTAINER, 999L);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals(0, response.getRemainingCapacity());
        assertEquals("Hết chỗ trống cho loại xe này", response.getMessage());
        verify(slotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hold slot successfully decrements available capacity")
    void testHoldSlotSuccess() {
        Slot availableSlot = Slot.builder()
                .id(1L)
                .parkingLot(ParkingLot.builder().id(parkingLotId).build())
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .totalCapacity(5)
                .availableCapacity(2)
                .build();
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.of(availableSlot));

        PricingRule pricing = PricingRule.builder()
                .basePricePerHour(new BigDecimal("25000"))
                .build();
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.of(pricing));

        SlotHoldResponse response = slotService.holdSlot(parkingLotId, VehicleTypeEnum.CONTAINER, 999L);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(1, response.getRemainingCapacity());
        assertEquals(new BigDecimal("25000"), response.getBaseHourlyPrice());
        assertEquals(1, availableSlot.getAvailableCapacity());
        verify(slotRepository, times(1)).save(availableSlot);
    }

    @Test
    @DisplayName("Release slot increments available capacity when below total capacity")
    void testReleaseSlotSuccess() {
        Slot slot = Slot.builder()
                .id(1L)
                .parkingLot(ParkingLot.builder().id(parkingLotId).build())
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(10)
                .availableCapacity(4)
                .build();
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.of(slot));

        slotService.releaseSlot(parkingLotId, VehicleTypeEnum.SEDAN_SUV, 123L);

        assertEquals(5, slot.getAvailableCapacity());
        verify(slotRepository, times(1)).save(slot);
    }

    @Test
    @DisplayName("Configure pricing rules fails when duplicate vehicle types are submitted")
    void testConfigurePricingRulesDuplicateType() {
        ParkingLot lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        PricingRuleRequest r1 = PricingRuleRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("20000"))
                .build();
        PricingRuleRequest r2 = PricingRuleRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("25000"))
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                slotService.configurePricingRules(merchantUserId, parkingLotId, List.of(r1, r2)));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("chỉ được cấu hình bảng giá một lần"));
    }

    @Test
    @DisplayName("Configure pricing rules successfully saves pricing rules")
    void testConfigurePricingRulesSuccess() {
        ParkingLot lot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        PricingRule rule = PricingRule.builder()
                .id(1L)
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("20000"))
                .build();
        when(pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, VehicleTypeEnum.SEDAN_SUV))
                .thenReturn(Optional.empty());
        when(pricingRuleRepository.save(any(PricingRule.class))).thenReturn(rule);

        PricingRuleRequest r1 = PricingRuleRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(new BigDecimal("20000"))
                .build();

        List<PricingRuleDto> results = slotService.configurePricingRules(merchantUserId, parkingLotId, List.of(r1));
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("Hold slot throws SLOT_NOT_AVAILABLE when slot is not configured")
    void testHoldSlotNotConfigured() {
        when(slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () ->
                slotService.holdSlot(parkingLotId, VehicleTypeEnum.CONTAINER, 123L));
        assertEquals(ErrorCode.SLOT_NOT_AVAILABLE, ex.getErrorCode());
    }

    @Test
    @DisplayName("getSlots and getPricingRules return repository contents")
    void testGetSlotsAndPricingRules() {
        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(List.of(Slot.builder().id(1L).vehicleType(VehicleTypeEnum.CONTAINER).build()));
        when(pricingRuleRepository.findByParkingLotId(parkingLotId)).thenReturn(List.of(PricingRule.builder().id(2L).vehicleType(VehicleTypeEnum.CONTAINER).basePricePerHour(BigDecimal.TEN).build()));

        List<SlotDto> slots = slotService.getSlots(parkingLotId);
        List<PricingRuleDto> rules = slotService.getPricingRules(parkingLotId);

        assertEquals(1, slots.size());
        assertEquals(1, rules.size());
    }
}
