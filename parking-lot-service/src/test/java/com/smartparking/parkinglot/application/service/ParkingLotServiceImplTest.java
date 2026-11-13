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

    @Mock
    private com.smartparking.common.messaging.TransactionalEventOutbox adminEventOutbox;

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
        verify(adminEventOutbox).enqueue(eq(com.smartparking.common.event.AdminOperationEvent.TOPIC), eq("parking-lot:10"),
                argThat(event -> event instanceof com.smartparking.common.event.AdminOperationEvent operation
                        && operation.getEventType().equals("PARKING_LOT_REVIEW_REQUESTED")
                        && operation.getSubmissionVersion() == 1L));
    }

    @Test
    void resubmissionPublishesOneNewVersionAndSubsequentEditsCannotPublishAgain() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId)
                .status(ParkingLotStatusEnum.CHANGES_REQUESTED).reviewSubmissionVersion(3).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));
        when(parkingLotRepository.save(lot)).thenReturn(lot);
        when(slotRepository.findByParkingLotId(parkingLotId)).thenReturn(Collections.emptyList());
        when(pricingRuleRepository.findByParkingLotId(parkingLotId)).thenReturn(Collections.emptyList());
        when(parkingLotImageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(parkingLotId)).thenReturn(Collections.emptyList());
        var request = CreateParkingLotRequest.builder().name("Hồ sơ đã sửa").address("Địa chỉ").build();
        parkingLotService.updateParkingLot(merchantUserId, parkingLotId, request);
        assertEquals(4, lot.getReviewSubmissionVersion());
        verify(adminEventOutbox).enqueue(any(), any(), argThat(event ->
                ((com.smartparking.common.event.AdminOperationEvent) event).getSubmissionVersion() == 4L));
        assertThrows(AppException.class, () -> parkingLotService.updateParkingLot(merchantUserId, parkingLotId, request));
        verifyNoMoreInteractions(adminEventOutbox);
    }

    @Test
    @DisplayName("Update parking lot fails when user is not the merchant owner")
    void testUpdateParkingLotForbidden() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(999L).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));

        CreateParkingLotRequest request = CreateParkingLotRequest.builder().name("Test").address("Test").build();

        AppException ex = assertThrows(AppException.class, () -> parkingLotService.updateParkingLot(merchantUserId, parkingLotId, request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Update parking lot fails if status is not REJECTED")
    void testUpdateParkingLotNotRejected() {
        ParkingLot lot = ParkingLot.builder().id(parkingLotId).merchantId(merchantUserId).status(ParkingLotStatusEnum.APPROVED).build();
        when(parkingLotRepository.findByIdForUpdate(parkingLotId)).thenReturn(Optional.of(lot));

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

    @Test
    @DisplayName("getAllParkingLotsForAdmin returns page of all lots")
    void testGetAllParkingLotsForAdmin() {
        ParkingLot lot = ParkingLot.builder().id(10L).name("Lot Admin").merchantId(1L).build();
        when(parkingLotRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(lot)));

        var res = parkingLotService.getAllParkingLotsForAdmin(PageRequest.of(0, 10));

        assertNotNull(res);
        assertEquals(1, res.getContent().size());
        assertEquals("Lot Admin", res.getContent().get(0).getName());
    }

    @Test
    @DisplayName("getParkingLotDetailForAdmin returns full detail with slots and pricing")
    void testGetParkingLotDetailForAdmin() {
        ParkingLot lot = ParkingLot.builder().id(10L).merchantId(1L).name("Detail Lot").build();
        when(parkingLotRepository.findById(10L)).thenReturn(Optional.of(lot));
        when(slotRepository.findByParkingLotId(10L)).thenReturn(List.of());
        when(pricingRuleRepository.findByParkingLotId(10L)).thenReturn(List.of());
        when(parkingLotImageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(10L)).thenReturn(List.of());

        var res = parkingLotService.getParkingLotDetailForAdmin(10L);

        assertNotNull(res);
        assertEquals("Detail Lot", res.getName());
    }

    @Test
    @DisplayName("approveParkingLot updates status to APPROVED")
    void testApproveParkingLot() {
        ParkingLot lot = ParkingLot.builder().id(10L).status(ParkingLotStatusEnum.PENDING_APPROVAL).build();
        when(parkingLotRepository.findById(10L)).thenReturn(Optional.of(lot));

        parkingLotService.approveParkingLot(1L, 10L, "Duyệt");

        assertEquals(ParkingLotStatusEnum.APPROVED, lot.getStatus());
        assertEquals("Duyệt", lot.getReviewReason());
        verify(parkingLotRepository, times(1)).save(lot);
    }

    @Test
    @DisplayName("rejectParkingLot updates status to REJECTED")
    void testRejectParkingLot() {
        ParkingLot lot = ParkingLot.builder().id(10L).status(ParkingLotStatusEnum.PENDING_APPROVAL).build();
        when(parkingLotRepository.findById(10L)).thenReturn(Optional.of(lot));

        parkingLotService.rejectParkingLot(1L, 10L, "Từ chối");

        assertEquals(ParkingLotStatusEnum.REJECTED, lot.getStatus());
        assertEquals("Từ chối", lot.getReviewReason());
        verify(parkingLotRepository, times(1)).save(lot);
    }

    @Test
    @DisplayName("requestParkingLotChanges updates status to CHANGES_REQUESTED")
    void testRequestParkingLotChanges() {
        ParkingLot lot = ParkingLot.builder().id(10L).status(ParkingLotStatusEnum.PENDING_APPROVAL).build();
        when(parkingLotRepository.findById(10L)).thenReturn(Optional.of(lot));

        parkingLotService.requestParkingLotChanges(1L, 10L, "Bổ sung ảnh");

        assertEquals(ParkingLotStatusEnum.CHANGES_REQUESTED, lot.getStatus());
        assertEquals("Bổ sung ảnh", lot.getReviewReason());
        verify(parkingLotRepository, times(1)).save(lot);
    }
}
