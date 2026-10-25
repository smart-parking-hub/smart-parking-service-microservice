package com.smartparking.parkinglot.api;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.parkinglot.api.controller.AdminParkingLotController;
import com.smartparking.parkinglot.application.dto.AdminDecisionRequest;
import com.smartparking.parkinglot.application.dto.AdminParkingLotDetailResponse;
import com.smartparking.parkinglot.application.dto.AdminParkingLotResponse;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.infrastructure.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminParkingLotControllerTest {

    @Mock
    private ParkingLotService parkingLotService;

    @InjectMocks
    private AdminParkingLotController controller;

    private final AuthenticatedUser adminUser = new AuthenticatedUser(1L, "0987654321", "ROLE_ADMIN");

    @Test
    @DisplayName("getAllParkingLots returns page of admin lots")
    void testGetAllParkingLots() {
        AdminParkingLotResponse item = AdminParkingLotResponse.builder().id(10L).name("Lot A").build();
        when(parkingLotService.getAllParkingLotsForAdmin(any()))
                .thenReturn(new PageResponse<>(List.of(item), 0, 1, 1));

        var res = controller.getAllParkingLots(PageRequest.of(0, 10));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(1, res.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getParkingLotDetail returns admin detail")
    void testGetParkingLotDetail() {
        AdminParkingLotDetailResponse detail = AdminParkingLotDetailResponse.builder().id(10L).build();
        when(parkingLotService.getParkingLotDetailForAdmin(10L)).thenReturn(detail);

        var res = controller.getParkingLotDetail(10L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertNotNull(res.getBody());
        assertEquals(10L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("approveParkingLot delegates to service")
    void testApproveParkingLot() {
        AdminDecisionRequest req = AdminDecisionRequest.builder().reason("Hồ sơ hợp lệ").build();

        var res = controller.approveParkingLot(adminUser, 10L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(parkingLotService, times(1)).approveParkingLot(1L, 10L, "Hồ sơ hợp lệ");
    }

    @Test
    @DisplayName("rejectParkingLot delegates to service")
    void testRejectParkingLot() {
        AdminDecisionRequest req = AdminDecisionRequest.builder().reason("Thiếu giấy phép").build();

        var res = controller.rejectParkingLot(adminUser, 10L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(parkingLotService, times(1)).rejectParkingLot(1L, 10L, "Thiếu giấy phép");
    }

    @Test
    @DisplayName("requestParkingLotChanges delegates to service")
    void testRequestParkingLotChanges() {
        AdminDecisionRequest req = AdminDecisionRequest.builder().reason("Bổ sung ảnh cổng vào").build();

        var res = controller.requestParkingLotChanges(adminUser, 10L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(parkingLotService, times(1)).requestParkingLotChanges(1L, 10L, "Bổ sung ảnh cổng vào");
    }
}
