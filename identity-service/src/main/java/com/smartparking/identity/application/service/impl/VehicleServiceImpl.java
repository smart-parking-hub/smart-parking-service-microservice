package com.smartparking.identity.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.VehicleStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.util.Pageables;
import com.smartparking.identity.application.dto.AddVehicleRequest;
import com.smartparking.identity.application.dto.UpdateVehicleRequest;
import com.smartparking.identity.application.dto.VehicleDto;
import com.smartparking.identity.application.service.VehicleService;
import com.smartparking.identity.domain.model.Driver;
import com.smartparking.identity.domain.model.Vehicle;
import com.smartparking.identity.domain.repository.DriverRepository;
import com.smartparking.identity.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VehicleServiceImpl implements VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    @Override
    @Transactional(readOnly = true)
    public VehicleDto getOwnedVehicle(Long userId, Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Phương tiện không tồn tại"));
        if (!vehicle.getDriver().getUser().getId().equals(userId) || vehicle.getStatus() != VehicleStatusEnum.ACTIVE)
            throw new AppException(ErrorCode.FORBIDDEN, "Phương tiện không thuộc tài khoản hoặc đã ngừng hoạt động");
        return VehicleDto.fromEntity(vehicle);
    }

    @Override
    @Transactional
    public VehicleDto addVehicle(Long driverUserId, AddVehicleRequest request) {
        Driver driver = driverRepository.findByUserId(driverUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ tài xế!"));

        if (vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new AppException(ErrorCode.CONFLICT, "Biển số xe đã tồn tại trong hệ thống!");
        }

        Vehicle saved = vehicleRepository.save(Vehicle.builder()
                .driver(driver)
                .licensePlate(request.getLicensePlate())
                .vehicleType(request.getVehicleType())
                .weightTon(request.getWeightTon())
                .brandModel(normalizeBrandModel(request.getBrandModel()))
                .status(VehicleStatusEnum.ACTIVE)
                .build());

        return VehicleDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public VehicleDto updateVehicle(Long driverUserId, Long vehicleId, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Phương tiện không tồn tại!"));

        if (!vehicle.getDriver().getUser().getId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền sửa phương tiện này!");
        }

        if (!vehicle.getLicensePlate().equals(request.getLicensePlate()) &&
                vehicleRepository.existsByLicensePlate(request.getLicensePlate())) {
            throw new AppException(ErrorCode.CONFLICT, "Biển số xe đã tồn tại trong hệ thống!");
        }

        vehicle.setLicensePlate(request.getLicensePlate());
        vehicle.setVehicleType(request.getVehicleType());
        vehicle.setWeightTon(request.getWeightTon());
        vehicle.setBrandModel(normalizeBrandModel(request.getBrandModel()));

        return VehicleDto.fromEntity(vehicleRepository.save(vehicle));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<VehicleDto> getMyVehicles(Long driverUserId, Pageable pageable) {
        Driver driver = driverRepository.findByUserId(driverUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy hồ sơ tài xế!"));

        Pageable safe = Pageables.sanitize(pageable);
        Page<VehicleDto> dtoPage = vehicleRepository.findByDriverId(driver.getId(), safe)
                .map(VehicleDto::fromEntity);

        return new PageResponse<>(dtoPage);
    }

    @Override
    @Transactional
    public void deleteVehicle(Long driverUserId, Long vehicleId) {
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Phương tiện không tồn tại!"));

        if (!vehicle.getDriver().getUser().getId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xóa phương tiện này!");
        }

        vehicle.setStatus(VehicleStatusEnum.INACTIVE);
        vehicleRepository.save(vehicle);
    }

    private String normalizeBrandModel(String brandModel) {
        if (brandModel == null) return null;
        String trimmed = brandModel.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
