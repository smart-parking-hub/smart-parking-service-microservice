package com.smartparking.identity.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.VehicleStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.identity.application.dto.AddVehicleRequest;
import com.smartparking.identity.application.dto.UpdateVehicleRequest;
import com.smartparking.identity.application.dto.VehicleDto;
import com.smartparking.identity.application.service.impl.VehicleServiceImpl;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.User;
import com.smartparking.identity.domain.model.Vehicle;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.VehicleRepository;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceImplTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DriverRepository driverRepository;

    @InjectMocks
    private VehicleServiceImpl vehicleService;

    private final Long driverUserId = 50L;
    private final Long driverId = 1L;

    @Test
    @DisplayName("Add vehicle fails when driver profile not found")
    void testAddVehicleDriverNotFound() {
        AddVehicleRequest req = new AddVehicleRequest();
        req.setLicensePlate("51A-99999");
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> vehicleService.addVehicle(driverUserId, req));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Add vehicle fails when license plate already exists")
    void testAddVehicleLicensePlateConflict() {
        AddVehicleRequest req = new AddVehicleRequest();
        req.setLicensePlate("51A-99999");

        Driver driver = Driver.builder().id(driverId).build();
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.existsByLicensePlate("51A-99999")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> vehicleService.addVehicle(driverUserId, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("Add vehicle successfully")
    void testAddVehicleSuccess() {
        AddVehicleRequest req = new AddVehicleRequest();
        req.setLicensePlate("51A-12345");
        req.setVehicleType(VehicleTypeEnum.CONTAINER);
        req.setWeightTon(new BigDecimal("15.5"));
        req.setBrandModel("Hyundai HD320");

        Driver driver = Driver.builder().id(driverId).build();
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.of(driver));
        when(vehicleRepository.existsByLicensePlate("51A-12345")).thenReturn(false);

        Vehicle saved = Vehicle.builder()
                .id(10L)
                .driver(driver)
                .licensePlate("51A-12345")
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .weightTon(new BigDecimal("15.5"))
                .brandModel("Hyundai HD320")
                .status(VehicleStatusEnum.ACTIVE)
                .build();
        when(vehicleRepository.save(any(Vehicle.class))).thenReturn(saved);

        VehicleDto dto = vehicleService.addVehicle(driverUserId, req);

        assertNotNull(dto);
        assertEquals("51A-12345", dto.getLicensePlate());
        assertEquals(VehicleTypeEnum.CONTAINER, dto.getVehicleType());
    }

    @Test
    @DisplayName("Update vehicle fails when driver is not the owner")
    void testUpdateVehicleForbidden() {
        User otherUser = User.builder().id(999L).build();
        Driver otherDriver = Driver.builder().id(2L).user(otherUser).build();
        Vehicle vehicle = Vehicle.builder().id(10L).driver(otherDriver).build();

        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));

        UpdateVehicleRequest req = new UpdateVehicleRequest();
        req.setLicensePlate("51A-88888");

        AppException ex = assertThrows(AppException.class, () -> vehicleService.updateVehicle(driverUserId, 10L, req));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("Delete vehicle marks status as INACTIVE")
    void testDeleteVehicleSuccess() {
        User user = User.builder().id(driverUserId).build();
        Driver driver = Driver.builder().id(driverId).user(user).build();
        Vehicle vehicle = Vehicle.builder().id(10L).driver(driver).status(VehicleStatusEnum.ACTIVE).build();

        when(vehicleRepository.findById(10L)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(driverUserId, 10L);

        assertEquals(VehicleStatusEnum.INACTIVE, vehicle.getStatus());
        verify(vehicleRepository, times(1)).save(vehicle);
    }

    @Test
    @DisplayName("Get my vehicles returns page of vehicles")
    void testGetMyVehicles() {
        Driver driver = Driver.builder().id(driverId).build();
        when(driverRepository.findByUserId(driverUserId)).thenReturn(Optional.of(driver));

        Vehicle v1 = Vehicle.builder().id(1L).licensePlate("51A-11111").vehicleType(VehicleTypeEnum.SEDAN_SUV).status(VehicleStatusEnum.ACTIVE).build();
        when(vehicleRepository.findByDriverId(eq(driverId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(v1), PageRequest.of(0, 10), 1));

        PageResponse<VehicleDto> result = vehicleService.getMyVehicles(driverUserId, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("51A-11111", result.getContent().get(0).getLicensePlate());
    }
}
