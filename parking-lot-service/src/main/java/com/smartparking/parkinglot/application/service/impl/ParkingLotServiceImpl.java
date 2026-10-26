package com.smartparking.parkinglot.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.util.Pageables;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.ParkingLotService;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.PricingRule;
import com.smartparking.parkinglot.domain.model.Slot;
import com.smartparking.parkinglot.domain.repository.ParkingLotImageRepository;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import com.smartparking.parkinglot.domain.repository.PricingRuleRepository;
import com.smartparking.parkinglot.domain.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ParkingLotServiceImpl implements ParkingLotService {

    private static final Set<String> MERCHANT_LIST_SORT_FIELDS = Set.of("id", "name", "address", "status", "createdAt");

    private final ParkingLotRepository parkingLotRepository;
    private final SlotRepository slotRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ParkingLotImageRepository parkingLotImageRepository;

    @Override
    @Transactional
    public ParkingLotDto createParkingLot(Long merchantUserId, CreateParkingLotRequest request) {
        if (parkingLotRepository.existsByName(request.getName().trim())) {
            throw new AppException(ErrorCode.CONFLICT, "Tên bãi đỗ đã tồn tại!");
        }

        ParkingLot lot = ParkingLot.builder()
                .merchantId(merchantUserId)
                .name(request.getName().trim())
                .address(request.getAddress().trim())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .status(ParkingLotStatusEnum.PENDING_APPROVAL)
                .ratingAverage(BigDecimal.ZERO)
                .build();

        ParkingLot saved = parkingLotRepository.save(lot);
        log.info("Created parking lot {} for merchant {}", saved.getId(), merchantUserId);
        return toDetail(saved);
    }

    @Override
    @Transactional
    public ParkingLotDto updateParkingLot(Long merchantUserId, Long parkingLotId, CreateParkingLotRequest request) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        if (lot.getStatus() != ParkingLotStatusEnum.REJECTED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ được chỉnh sửa hồ sơ bị từ chối!");
        }

        lot.setName(request.getName().trim());
        lot.setAddress(request.getAddress().trim());
        lot.setLatitude(request.getLatitude());
        lot.setLongitude(request.getLongitude());
        lot.setStatus(ParkingLotStatusEnum.PENDING_APPROVAL);
        lot.setReviewReason(null);
        lot.setReviewedAt(null);
        lot.setReviewedBy(null);

        return toDetail(parkingLotRepository.save(lot));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MerchantParkingLotListResponse> getMerchantParkingLots(
            Long merchantUserId,
            ParkingLotStatusEnum status,
            String keyword,
            int page,
            int size,
            String sort) {

        String[] sortParts = sort == null ? new String[0] : sort.split(",", -1);
        String sortField = "createdAt";
        Sort.Direction direction = Sort.Direction.DESC;

        if (sortParts.length == 2 && MERCHANT_LIST_SORT_FIELDS.contains(sortParts[0])) {
            sortField = sortParts[0];
            try {
                direction = Sort.Direction.fromString(sortParts[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortField));

        Page<MerchantParkingLotListResponse> parkingLots = parkingLotRepository
                .searchOwnedParkingLots(merchantUserId, status, normalizedKeyword, pageable)
                .map(MerchantParkingLotListResponse::fromEntity);

        return new PageResponse<>(parkingLots);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ParkingLotSearchResponse> searchParkingLots(
            VehicleTypeEnum vehicleType,
            boolean includeFull,
            Double latitude,
            Double longitude,
            Double radiusKm,
            Pageable pageable) {

        boolean geoFilter = latitude != null && longitude != null;
        double radius = (radiusKm != null && radiusKm > 0) ? radiusKm : 50.0;
        Pageable safePageable = Pageables.sanitize(pageable);

        if (!geoFilter) {
            Page<ParkingLot> page = parkingLotRepository.searchApprovedByVehicleType(vehicleType, includeFull, safePageable);
            return new PageResponse<>(page.map(lot -> toSearchResponse(lot, vehicleType, null)));
        }

        List<ParkingLotSearchResponse> matched = parkingLotRepository
                .findApprovedByVehicleType(vehicleType, includeFull)
                .stream()
                .map(lot -> {
                    Double distance = distanceKm(latitude, longitude, lot.getLatitude(), lot.getLongitude());
                    return toSearchResponse(lot, vehicleType, distance);
                })
                .filter(item -> item.getDistanceKm() != null && item.getDistanceKm() <= radius)
                .sorted(Comparator.comparing(ParkingLotSearchResponse::getDistanceKm,
                        Comparator.nullsLast(Double::compareTo)))
                .collect(Collectors.toList());

        int pageNumber = safePageable.getPageNumber();
        int pageSize = safePageable.getPageSize();
        int from = Math.min(pageNumber * pageSize, matched.size());
        int to = Math.min(from + pageSize, matched.size());
        List<ParkingLotSearchResponse> content = matched.subList(from, to);
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil(matched.size() / (double) pageSize);

        return PageResponse.<ParkingLotSearchResponse>builder()
                .content(content)
                .currentPage(pageNumber)
                .totalPages(totalPages)
                .totalElements(matched.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ParkingLotDto getDetail(Long id) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ với ID: " + id));
        return toDetail(lot);
    }

    @Override
    @Transactional(readOnly = true)
    public ParkingLotDto getMerchantDetail(Long merchantUserId, Long id) {
        return toDetail(getOwnedParkingLot(merchantUserId, id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminParkingLotResponse> getAllParkingLotsForAdmin(Pageable pageable) {
        Pageable safe = Pageables.sanitize(pageable);
        if (safe.getSort().isUnsorted()) {
            safe = PageRequest.of(safe.getPageNumber(), safe.getPageSize(), Sort.by(Sort.Direction.DESC, "id"));
        }
        Page<ParkingLot> page = parkingLotRepository.findAll(safe);
        return new PageResponse<>(page.map(AdminParkingLotResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminParkingLotDetailResponse getParkingLotDetailForAdmin(Long id) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ"));
        List<SlotDto> slots = slotRepository.findByParkingLotId(lot.getId()).stream().map(SlotDto::fromEntity).toList();
        List<PricingRuleDto> pricing = pricingRuleRepository.findByParkingLotId(lot.getId()).stream().map(PricingRuleDto::fromEntity).toList();
        List<ParkingLotImageResponse> images = parkingLotImageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(lot.getId())
                .stream().map(ParkingLotImageResponse::fromEntity).toList();
        return AdminParkingLotDetailResponse.from(lot, slots, pricing, images);
    }

    @Override
    @Transactional
    public void approveParkingLot(Long adminUserId, Long id, String reason) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ"));
        lot.setStatus(ParkingLotStatusEnum.APPROVED);
        lot.setReviewReason(reason);
        lot.setReviewedBy(adminUserId);
        lot.setReviewedAt(LocalDateTime.now());
        parkingLotRepository.save(lot);
    }

    @Override
    @Transactional
    public void rejectParkingLot(Long adminUserId, Long id, String reason) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ"));
        lot.setStatus(ParkingLotStatusEnum.REJECTED);
        lot.setReviewReason(reason);
        lot.setReviewedBy(adminUserId);
        lot.setReviewedAt(LocalDateTime.now());
        parkingLotRepository.save(lot);
    }

    @Override
    @Transactional
    public void requestParkingLotChanges(Long adminUserId, Long id, String reason) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ"));
        lot.setStatus(ParkingLotStatusEnum.CHANGES_REQUESTED);
        lot.setReviewReason(reason);
        lot.setReviewedBy(adminUserId);
        lot.setReviewedAt(LocalDateTime.now());
        parkingLotRepository.save(lot);
    }

    private ParkingLot getOwnedParkingLot(Long merchantUserId, Long id) {
        ParkingLot lot = parkingLotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Không tìm thấy bãi đỗ"));
        if (!lot.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền quản lý bãi đỗ này");
        }
        return lot;
    }

    private ParkingLotDto toDetail(ParkingLot lot) {
        ParkingLotDto dto = ParkingLotDto.fromEntity(lot);
        dto.setSlots(slotRepository.findByParkingLotId(lot.getId()).stream().map(SlotDto::fromEntity).toList());
        dto.setPricingRules(pricingRuleRepository.findByParkingLotId(lot.getId()).stream().map(PricingRuleDto::fromEntity).toList());
        dto.setImages(parkingLotImageRepository.findByParkingLotIdOrderByCoverDescCreatedAtAsc(lot.getId()).stream()
                .map(ParkingLotImageResponse::fromEntity)
                .toList());
        return dto;
    }

    private ParkingLotSearchResponse toSearchResponse(ParkingLot lot, VehicleTypeEnum vehicleType, Double distanceKm) {
        int available = slotRepository.findByParkingLotIdAndVehicleType(lot.getId(), vehicleType)
                .map(Slot::getAvailableCapacity)
                .orElse(0);

        Double avgRating = lot.getRatingAverage() != null ? lot.getRatingAverage().doubleValue() : 0.0;
        BigDecimal basePrice = pricingRuleRepository
                .findByParkingLotIdAndVehicleType(lot.getId(), vehicleType)
                .map(PricingRule::getBasePricePerHour)
                .orElse(null);

        return ParkingLotSearchResponse.builder()
                .id(lot.getId())
                .name(lot.getName())
                .address(lot.getAddress())
                .latitude(lot.getLatitude())
                .longitude(lot.getLongitude())
                .availableCapacity(available)
                .basePricePerHour(basePrice)
                .averageRating(avgRating)
                .distanceKm(distanceKm)
                .build();
    }

    public static Double distanceKm(double lat, double lng, BigDecimal lotLat, BigDecimal lotLng) {
        if (lotLat == null || lotLng == null) {
            return null;
        }
        double lat1 = Math.toRadians(lat);
        double lat2 = Math.toRadians(lotLat.doubleValue());
        double dLat = Math.toRadians(lotLat.doubleValue() - lat);
        double dLng = Math.toRadians(lotLng.doubleValue() - lng);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = 6371.0 * c;
        return Math.round(distance * 100.0) / 100.0;
    }
}
