package com.smartparking.parkinglot.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.impl.ParkingLotServiceImpl;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.Slot;
import com.smartparking.parkinglot.domain.repository.ParkingLotImageRepository;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import com.smartparking.parkinglot.domain.repository.PricingRuleRepository;
import com.smartparking.parkinglot.domain.repository.SlotRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotServiceImplTest {

    @Mock
    private ParkingLotRepository parkingLotRepository;

    @Mock
    private SlotRepository slotRepository;

    @Mock
    private PricingRuleRepository pricingRuleRepository;

    @Mock
    private ParkingLotImageRepository parkingLotImageRepository;

    @InjectMocks
    private ParkingLotServiceImpl parkingLotService;

    private final Long merchantUserId = 100L;
    private final Long parkingLotId = 10L;

    @Test
    @DisplayName("Create parking lot fails when name already exists")
    void testCreateParkingLotDuplicateName() {
        CreateParkingLotRequest request = CreateParkingLotRequest.builder()
                .name("Bãi Đỗ Trung Tâm")
                .address("123 Lê Duẩn, Q1")
                .latitude(new BigDecimal("10.780"))
                .longitude(new BigDecimal("106.700"))
                .build();

        when(parkingLotRepository.existsByName("Bãi Đỗ Trung Tâm")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.createParkingLot(merchantUserId, request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Create parking lot successfully with PENDING_APPROVAL status")
    void testCreateParkingLotSuccess() {
        CreateParkingLotRequest request = CreateParkingLotRequest.builder()
                .name("Bãi Đỗ Trung Tâm")
                .address("123 Lê Duẩn, Q1")
                .latitude(new BigDecimal("10.780"))
                .longitude(new BigDecimal("106.700"))
                .build();

        when(parkingLotRepository.existsByName("Bãi Đỗ Trung Tâm")).thenReturn(false);

        ParkingLot savedLot = ParkingLot.builder()
                .id(parkingLotId)
                .merchantId(merchantUserId)
                .name("Bãi Đỗ Trung Tâm")
                .address("123 Lê Duẩn, Q1")
                .latitude(new BigDecimal("10.780"))
                .longitude(new BigDecimal("106.700"))
                .status(ParkingLotStatusEnum.PENDING_APPROVAL)
                .build();
        when(parkingLotRepository.save(any(ParkingLot.class))).thenReturn(savedLot);
        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(Collections.emptyList());
        when(pricingRuleRepository.findByParkingLotId(parkingLotId)).thenReturn(Collections.emptyList());
        when(parkingLotImageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId)).thenReturn(Collections.emptyList());

        ParkingLotDto dto = parkingLotService.createParkingLot(merchantUserId, request);

        assertNotNull(dto);
        assertEquals("Bãi Đỗ Trung Tâm", dto.getName());
        assertEquals(ParkingLotStatusEnum.PENDING_APPROVAL, dto.getStatus());
    }

    @Test
    @DisplayName("Update parking lot fails when user is not the merchant owner")
    void testUpdateParkingLotForbidden() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(999L).build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        CreateParkingLotRequest request = CreateParkingLotRequest.builder().name("Test").address("Test").build();

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.updateParkingLot(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Update parking lot fails if status is not REJECTED")
    void testUpdateParkingLotNotRejected() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).status(ParkingLotStatusEnum.APPROVED).build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        CreateParkingLotRequest request = CreateParkingLotRequest.builder().name("Test").address("Test").build();

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.updateParkingLot(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get detail throws PARKING_LOT_NOT_FOUND when not found")
    void testGetDetailNotFound() {
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.getDetail(parkingLotId));
        assertEquals(ErrorCode.PARKING_LOT_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Get merchant detail throws FORBIDDEN when accessed by non-owner")
    void testGetMerchantDetailForbidden() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(999L).build();
        when(parkingLotRepository.findById(parkingLotId)).thenReturn(Optional.of(lot));

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.getMerchantDetail(merchantUserId, parkingLotId));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Calculate distance with Haversine formula correctly")
    void testDistanceKm() {
        // Distance between Hanoi (21.0285, 105.8542) and HCMC (10.7769, 106.7008) is ~1130-1150 km
        Double distance = ParkingLotServiceImpl.distanceKm(
                21.0285, 105.8542,
                new BigDecimal("10.7769"), new BigDecimal("106.7008")
        );
        assertNotNull(distance);
        assertTrue(distance > 1100 && distance < 1200);

        // Distance with null coordinates returns null safely
        assertNull(ParkingLotServiceImpl.distanceKm(21.0, 105.0, null, null));
    }

    @Test
    @DisplayName("Search parking lots without geo filter delegates to searchApprovedByVehicleType")
    void testSearchWithoutGeoFilter() {
        ParkingLot lot = ParkingLot.builder()
                .id(1L)
                .name("Lot 1")
                .address("Addr 1")
                .status(ParkingLotStatusEnum.APPROVED)
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(parkingLotRepository.searchApprovedByVehicleType(eq(VehicleTypeEnum.CONTAINER), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(lot), pageable, 1));
        when(slotRepository.findByParkingLotIdAndVehicleType(1L, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.of(Slot.builder().availableCapacity(5).build()));

        PageResponse<ParkingLotSearchResponse> response = parkingLotService.searchParkingLots(
                VehicleTypeEnum.CONTAINER, true, null, null, null, pageable
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Lot 1", response.getContent().get(0).getName());
        assertEquals(5, response.getContent().get(0).getAvailableCapacity());
    }

    @Test
    @DisplayName("Search parking lots with geo filter filters by radius")
    void testSearchWithGeoFilter() {
        ParkingLot nearLot = ParkingLot.builder()
                .id(1L)
                .name("Near Lot")
                .latitude(new BigDecimal("10.780"))
                .longitude(new BigDecimal("106.700"))
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        ParkingLot farLot = ParkingLot.builder()
                .id(2L)
                .name("Far Lot")
                .latitude(new BigDecimal("21.0285")) // Hanoi ~1150km away
                .longitude(new BigDecimal("105.8542"))
                .status(ParkingLotStatusEnum.APPROVED)
                .build();

        PageRequest pageable = PageRequest.of(0, 10);
        when(parkingLotRepository.findApprovedByVehicleType(eq(VehicleTypeEnum.CONTAINER), eq(true)))
                .thenReturn(List.of(nearLot, farLot));
        when(slotRepository.findByParkingLotIdAndVehicleType(1L, VehicleTypeEnum.CONTAINER))
                .thenReturn(Optional.of(Slot.builder().availableCapacity(3).build()));

        PageResponse<ParkingLotSearchResponse> response = parkingLotService.searchParkingLots(
                VehicleTypeEnum.CONTAINER, true, 10.780, 106.700, 5.0, pageable
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("Near Lot", response.getContent().get(0).getName());
    }

    @Test
    @DisplayName("getMerchantParkingLots returns merchant lots")
    void testGetMerchantParkingLots() {
        ParkingLot lot = ParkingLot.builder()
                .id(10L)
                .merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.APPROVED)
                .build();
        when(parkingLotRepository.searchOwnedParkingLots(eq(merchantUserId), eq(ParkingLotStatusEnum.APPROVED), any(), any()))
                .thenReturn(new PageImpl<>(List.of(lot)));

        PageResponse<MerchantParkingLotListResponse> res = parkingLotService.getMerchantParkingLots(
                merchantUserId, ParkingLotStatusEnum.APPROVED, null, 0, 10, "createdAt,desc"
        );

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
    }
}
