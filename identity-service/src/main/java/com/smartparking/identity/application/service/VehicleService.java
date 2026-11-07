package com.smartparking.identity.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.identity.application.dto.AddVehicleRequest;
import com.smartparking.identity.application.dto.UpdateVehicleRequest;
import com.smartparking.identity.application.dto.VehicleDto;
import org.springframework.data.domain.Pageable;

public interface VehicleService {
    VehicleDto getOwnedVehicle(Long driverUserId, Long vehicleId);
    VehicleDto addVehicle(Long driverUserId, AddVehicleRequest request);
    VehicleDto updateVehicle(Long driverUserId, Long vehicleId, UpdateVehicleRequest request);
    PageResponse<VehicleDto> getMyVehicles(Long driverUserId, Pageable pageable);
    void deleteVehicle(Long driverUserId, Long vehicleId);
}
