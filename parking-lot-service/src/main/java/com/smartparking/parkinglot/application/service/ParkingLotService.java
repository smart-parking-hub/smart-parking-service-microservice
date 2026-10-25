package com.smartparking.parkinglot.application.service;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.application.dto.*;
import org.springframework.data.domain.Pageable;

public interface ParkingLotService {
    ParkingLotDto createParkingLot(Long merchantUserId, CreateParkingLotRequest request);
    ParkingLotDto updateParkingLot(Long merchantUserId, Long parkingLotId, CreateParkingLotRequest request);
    PageResponse<MerchantParkingLotListResponse> getMerchantParkingLots(
            Long merchantUserId,
            ParkingLotStatusEnum status,
            String keyword,
            int page,
            int size,
            String sort);
    PageResponse<ParkingLotSearchResponse> searchParkingLots(
            VehicleTypeEnum vehicleType,
            boolean includeFull,
            Double latitude,
            Double longitude,
            Double radiusKm,
            Pageable pageable);
    ParkingLotDto getDetail(Long id);
    ParkingLotDto getMerchantDetail(Long merchantUserId, Long id);
    PageResponse<AdminParkingLotResponse> getAllParkingLotsForAdmin(Pageable pageable);
    AdminParkingLotDetailResponse getParkingLotDetailForAdmin(Long id);
    void approveParkingLot(Long adminUserId, Long id, String reason);
    void rejectParkingLot(Long adminUserId, Long id, String reason);
    void requestParkingLotChanges(Long adminUserId, Long id, String reason);
}
