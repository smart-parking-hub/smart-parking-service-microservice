package com.smartparking.parkinglot.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.api.controller.ParkingLotController;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParkingLotControllerTest {

    @Mock
    private ParkingLotService parkingLotService;

    @Mock
    private SlotService slotService;

    @InjectMocks
    private ParkingLotController controller;

    private AuthenticatedUser merchantUser;

    @BeforeEach
    void setUp() {
        merchantUser = new AuthenticatedUser(100L, "merchant01", "MERCHANT");
    }

    @Test
    @DisplayName("create parking lot returns CREATED status")
    void testCreate() {
        CreateParkingLotRequest req = CreateParkingLotRequest.builder().name("Lot A").build();
        ParkingLotDto dto = ParkingLotDto.builder().id(10L).name("Lot A").build();
        when(parkingLotService.createParkingLot(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<ParkingLotDto>> response = controller.create(merchantUser, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(10L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("update parking lot returns OK status")
    void testUpdate() {
        CreateParkingLotRequest req = CreateParkingLotRequest.builder().name("Lot Updated").build();
        ParkingLotDto dto = ParkingLotDto.builder().id(10L).name("Lot Updated").build();
        when(parkingLotService.updateParkingLot(100L, 10L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<ParkingLotDto>> response = controller.update(merchantUser, 10L, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Lot Updated", response.getBody().getData().getName());
    }

    @Test
    @DisplayName("getMerchantParkingLots returns paginated result")
    void testGetMerchantParkingLots() {
        PageResponse<MerchantParkingLotListResponse> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(parkingLotService.getMerchantParkingLots(eq(100L), any(), any(), anyInt(), anyInt(), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<MerchantParkingLotListResponse>>> response =
                controller.getMerchantParkingLots(merchantUser, 0, 10, ParkingLotStatusEnum.APPROVED, "kw", "createdAt,desc");

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("merchantDetail returns lot details")
    void testMerchantDetail() {
        ParkingLotDto detail = ParkingLotDto.builder().id(10L).build();
        when(parkingLotService.getMerchantDetail(100L, 10L)).thenReturn(detail);

        ResponseEntity<ApiResponse<ParkingLotDto>> response = controller.merchantDetail(merchantUser, 10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("search parking lots returns search results")
    void testSearch() {
        PageResponse<ParkingLotSearchResponse> searchResult = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(parkingLotService.searchParkingLots(any(), anyBoolean(), any(), any(), any(), any()))
                .thenReturn(searchResult);

        ResponseEntity<ApiResponse<PageResponse<ParkingLotSearchResponse>>> response =
                controller.search(VehicleTypeEnum.CONTAINER, true, 10.5, 106.5, 5.0, PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("detail returns public parking lot details")
    void testDetail() {
        ParkingLotDto dto = ParkingLotDto.builder().id(10L).build();
        when(parkingLotService.getDetail(10L)).thenReturn(dto);

        ResponseEntity<ApiResponse<ParkingLotDto>> response = controller.detail(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(10L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("configSlot delegates to slotService")
    void testConfigSlot() {
        SlotConfigRequest req = SlotConfigRequest.builder().vehicleType(VehicleTypeEnum.SEDAN_SUV).totalCapacity(20).build();
        SlotDto dto = SlotDto.builder().id(1L).build();
        when(slotService.configureSlot(100L, 10L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<SlotDto>> response = controller.configSlot(merchantUser, 10L, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("configSlots batch delegates to slotService")
    void testConfigSlots() {
        SlotConfigRequest req = SlotConfigRequest.builder().vehicleType(VehicleTypeEnum.SEDAN_SUV).totalCapacity(20).build();
        BulkSlotConfigRequest bulk = new BulkSlotConfigRequest();
        bulk.setSlots(List.of(req));
        when(slotService.configureSlots(eq(100L), eq(10L), any())).thenReturn(List.of(SlotDto.builder().id(1L).build()));

        ResponseEntity<ApiResponse<List<SlotDto>>> response = controller.configSlots(merchantUser, 10L, bulk);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("getSlots returns slots list")
    void testGetSlots() {
        when(slotService.getSlots(10L)).thenReturn(List.of(SlotDto.builder().id(1L).build()));

        ResponseEntity<ApiResponse<List<SlotDto>>> response = controller.getSlots(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("configPricing delegates to slotService")
    void testConfigPricing() {
        PricingRuleRequest req = PricingRuleRequest.builder().vehicleType(VehicleTypeEnum.SEDAN_SUV).basePricePerHour(BigDecimal.TEN).build();
        when(slotService.configurePricing(100L, 10L, req)).thenReturn(PricingRuleDto.builder().id(1L).build());

        ResponseEntity<ApiResponse<PricingRuleDto>> response = controller.configPricing(merchantUser, 10L, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("configPricingRules batch delegates to slotService")
    void testConfigPricingRules() {
        PricingRuleRequest req = PricingRuleRequest.builder().vehicleType(VehicleTypeEnum.SEDAN_SUV).basePricePerHour(BigDecimal.TEN).build();
        BulkPricingRuleRequest bulk = new BulkPricingRuleRequest();
        bulk.setPricingRules(List.of(req));
        when(slotService.configurePricingRules(eq(100L), eq(10L), any())).thenReturn(List.of(PricingRuleDto.builder().id(1L).build()));

        ResponseEntity<ApiResponse<List<PricingRuleDto>>> response = controller.configPricingRules(merchantUser, 10L, bulk);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("getPricing returns pricing rules list")
    void testGetPricing() {
        when(slotService.getPricingRules(10L)).thenReturn(List.of(PricingRuleDto.builder().id(1L).build()));

        ResponseEntity<ApiResponse<List<PricingRuleDto>>> response = controller.getPricing(10L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().size());
    }

    @Test
    @DisplayName("holdSlot and releaseSlot internal RPC endpoints")
    void testHoldAndReleaseSlot() {
        SlotHoldRequest req = SlotHoldRequest.builder().vehicleType(VehicleTypeEnum.SEDAN_SUV).bookingId(55L).build();
        SlotHoldResponse holdRes = SlotHoldResponse.builder().success(true).build();
        when(slotService.holdSlot(10L, VehicleTypeEnum.SEDAN_SUV, 55L)).thenReturn(holdRes);

        ResponseEntity<ApiResponse<SlotHoldResponse>> holdResp = controller.holdSlot(10L, req);
        assertEquals(HttpStatus.OK, holdResp.getStatusCode());
        assertTrue(holdResp.getBody().getData().isSuccess());

        ResponseEntity<ApiResponse<Void>> relResp = controller.releaseSlot(10L, req);
        assertEquals(HttpStatus.OK, relResp.getStatusCode());
        verify(slotService, times(1)).releaseSlot(10L, VehicleTypeEnum.SEDAN_SUV, 55L);
    }
}
