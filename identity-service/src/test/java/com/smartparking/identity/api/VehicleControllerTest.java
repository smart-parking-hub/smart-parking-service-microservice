package com.smartparking.identity.api;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.UserStatusEnum;
import com.smartparking.identity.application.dto.AddVehicleRequest;
import com.smartparking.identity.application.dto.UpdateVehicleRequest;
import com.smartparking.identity.application.dto.VehicleDto;
import com.smartparking.identity.application.service.VehicleService;
import com.smartparking.identity.infrastructure.security.CustomUserDetails;
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

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleControllerTest {

    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private VehicleController controller;

    private CustomUserDetails user;

    @BeforeEach
    void setUp() {
        user = new CustomUserDetails(100L, "0912345678", "pass", UserStatusEnum.ACTIVE, Collections.emptyList());
    }

    @Test
    @DisplayName("getMyVehicles returns paginated vehicles")
    void testGetMyVehicles() {
        PageResponse<VehicleDto> page = new PageResponse<>(new PageImpl<>(Collections.emptyList()));
        when(vehicleService.getMyVehicles(eq(100L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<VehicleDto>>> res = controller.getMyVehicles(user, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, res.getStatusCode());
    }

    @Test
    @DisplayName("addVehicle returns CREATED status")
    void testAddVehicle() {
        AddVehicleRequest req = new AddVehicleRequest();
        VehicleDto dto = VehicleDto.builder().id(1L).build();
        when(vehicleService.addVehicle(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<VehicleDto>> res = controller.addVehicle(user, req);

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("updateVehicle returns updated VehicleDto")
    void testUpdateVehicle() {
        UpdateVehicleRequest req = new UpdateVehicleRequest();
        VehicleDto dto = VehicleDto.builder().id(1L).build();
        when(vehicleService.updateVehicle(100L, 1L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<VehicleDto>> res = controller.updateVehicle(user, 1L, req);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1L, res.getBody().getData().getId());
    }

    @Test
    @DisplayName("deleteVehicle delegates to vehicleService")
    void testDeleteVehicle() {
        ResponseEntity<ApiResponse<Void>> res = controller.deleteVehicle(user, 1L);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(vehicleService, times(1)).deleteVehicle(100L, 1L);
    }
}
