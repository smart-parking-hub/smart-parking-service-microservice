package com.smartparking.parkinglot;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.PricingRule;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import com.smartparking.parkinglot.domain.repository.PricingRuleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class ParkingLotServiceTest {

    @Autowired
    private ParkingLotService parkingLotService;

    @Autowired
    private SlotService slotService;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private PricingRuleRepository pricingRuleRepository;

    private final Long merchantUserId = 100L;

    @Test
    @DisplayName("Create parking lot successfully with PENDING_APPROVAL status")
    void testCreateParkingLot() {
        CreateParkingLotRequest request = CreateParkingLotRequest.builder()
                .name("Bãi Đỗ Xe Quận 1 Central")
                .address("123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
                .latitude(new BigDecimal("10.776889"))
                .longitude(new BigDecimal("106.700806"))
                .build();

        ParkingLotDto result = parkingLotService.createParkingLot(merchantUserId, request);

        assertNotNull(result);
        assertNotNull(result.getId());
        assertEquals("Bãi Đỗ Xe Quận 1 Central", result.getName());
        assertEquals(ParkingLotStatusEnum.PENDING_APPROVAL, result.getStatus());
        assertEquals(merchantUserId, result.getMerchantId());

        // Duplicate name should fail
        assertThrows(AppException.class, () -> parkingLotService.createParkingLot(merchantUserId, request));
    }

    @Test
    @DisplayName("Hold and release slot updates capacity correctly")
    void testHoldAndReleaseSlot() {
        // Create lot
        CreateParkingLotRequest request = CreateParkingLotRequest.builder()
                .name("Bãi Đỗ Xe Thảo Điền")
                .address("45 Thảo Điền, TP Thủ Đức")
                .latitude(new BigDecimal("10.8032"))
                .longitude(new BigDecimal("106.7323"))
                .build();
        ParkingLotDto created = parkingLotService.createParkingLot(merchantUserId, request);

        // Approve lot directly for testing
        ParkingLot lot = parkingLotRepository.findById(created.getId()).orElseThrow();
        lot.setStatus(ParkingLotStatusEnum.APPROVED);
        parkingLotRepository.save(lot);

        // Configure slot capacity: 2 SEDAN_SUV
        SlotConfigRequest slotReq = SlotConfigRequest.builder()
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .totalCapacity(2)
                .build();
        SlotDto slot = slotService.configureSlot(merchantUserId, created.getId(), slotReq);
        assertEquals(2, slot.getAvailableCapacity());

        // Configure pricing rule for SEDAN_SUV
        pricingRuleRepository.save(PricingRule.builder()
                .parkingLot(lot)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .basePricePerHour(BigDecimal.valueOf(15000))
                .build());

        // Hold slot 1
        SlotHoldResponse hold1 = slotService.holdSlot(created.getId(), VehicleTypeEnum.SEDAN_SUV, 1L);
        assertTrue(hold1.isSuccess());
        assertEquals(1, hold1.getRemainingCapacity());

        // Hold slot 2
        SlotHoldResponse hold2 = slotService.holdSlot(created.getId(), VehicleTypeEnum.SEDAN_SUV, 2L);
        assertTrue(hold2.isSuccess());
        assertEquals(0, hold2.getRemainingCapacity());

        // Hold slot 3 -> should fail because capacity is full
        SlotHoldResponse hold3 = slotService.holdSlot(created.getId(), VehicleTypeEnum.SEDAN_SUV, 3L);
        assertFalse(hold3.isSuccess());

        // Release slot 1
        slotService.releaseSlot(created.getId(), VehicleTypeEnum.SEDAN_SUV, 1L);

        // Hold slot 3 should now succeed
        SlotHoldResponse holdAfterRelease = slotService.holdSlot(created.getId(), VehicleTypeEnum.SEDAN_SUV, 3L);
        assertTrue(holdAfterRelease.isSuccess());
    }
}
