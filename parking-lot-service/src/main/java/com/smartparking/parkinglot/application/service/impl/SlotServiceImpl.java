package com.smartparking.parkinglot.application.service.impl;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.*;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import com.smartparking.parkinglot.domain.model.PricingRule;
import com.smartparking.parkinglot.domain.model.Slot;
import com.smartparking.parkinglot.domain.repository.ParkingLotRepository;
import com.smartparking.parkinglot.domain.repository.PricingRuleRepository;
import com.smartparking.parkinglot.domain.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlotServiceImpl implements SlotService {

    private static final BigDecimal DEFAULT_SURGE_MULTIPLIER = new BigDecimal("1.00");
    private static final BigDecimal DEFAULT_OVERTIME_PENALTY_MULTIPLIER = new BigDecimal("1.50");

    private final SlotRepository slotRepository;
    private final com.smartparking.parkinglot.domain.repository.SlotHoldRepository slotHoldRepository;
    private final PricingRuleRepository pricingRuleRepository;
    private final ParkingLotRepository parkingLotRepository;

    @Override
    @Transactional
    public SlotDto configureSlot(Long merchantUserId, Long parkingLotId, SlotConfigRequest request) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotApprovedForConfig(lot);
        return configureSlotForLot(lot, request);
    }

    @Override
    @Transactional
    public List<SlotDto> configureSlots(Long merchantUserId, Long parkingLotId, List<SlotConfigRequest> requests) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotApprovedForConfig(lot);

        Set<VehicleTypeEnum> vehicleTypes = new HashSet<>();
        for (SlotConfigRequest request : requests) {
            if (!vehicleTypes.add(request.getVehicleType())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi loại xe chỉ được cấu hình một lần");
            }
        }

        return requests.stream()
                .map(request -> configureSlotForLot(lot, request))
                .collect(Collectors.toList());
    }

    private SlotDto configureSlotForLot(ParkingLot lot, SlotConfigRequest request) {
        Long parkingLotId = lot.getId();
        Slot savedSlot = slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, request.getVehicleType())
                .map(slot -> {
                    int occupiedCapacity = slot.getTotalCapacity() - slot.getAvailableCapacity();
                    if (request.getTotalCapacity() < occupiedCapacity) {
                        throw new AppException(ErrorCode.BAD_REQUEST,
                                "Không thể giảm sức chứa thấp hơn số chỗ đang sử dụng của loại xe " + request.getVehicleType());
                    }
                    slot.setTotalCapacity(request.getTotalCapacity());
                    slot.setAvailableCapacity(request.getTotalCapacity() - occupiedCapacity);
                    return slotRepository.save(slot);
                })
                .orElseGet(() -> slotRepository.save(Slot.builder()
                        .parkingLot(lot)
                        .vehicleType(request.getVehicleType())
                        .totalCapacity(request.getTotalCapacity())
                        .availableCapacity(request.getTotalCapacity())
                        .build()));
        return SlotDto.fromEntity(savedSlot);
    }

    @Override
    @Transactional
    public PricingRuleDto configurePricing(Long merchantUserId, Long parkingLotId, PricingRuleRequest request) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotApprovedForConfig(lot);
        return configurePricingForLot(lot, request);
    }

    @Override
    @Transactional
    public List<PricingRuleDto> configurePricingRules(Long merchantUserId, Long parkingLotId, List<PricingRuleRequest> requests) {
        ParkingLot lot = getOwnedParkingLot(merchantUserId, parkingLotId);
        assertLotApprovedForConfig(lot);

        Set<VehicleTypeEnum> vehicleTypes = new HashSet<>();
        for (PricingRuleRequest request : requests) {
            if (!vehicleTypes.add(request.getVehicleType())) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Mỗi loại xe chỉ được cấu hình bảng giá một lần");
            }
        }

        return requests.stream()
                .map(request -> configurePricingForLot(lot, request))
                .collect(Collectors.toList());
    }

    private PricingRuleDto configurePricingForLot(ParkingLot lot, PricingRuleRequest request) {
        PricingRule rule = pricingRuleRepository.findByParkingLotIdAndVehicleType(lot.getId(), request.getVehicleType())
                .map(existing -> {
                    existing.setBasePricePerHour(request.getBasePricePerHour());
                    existing.setSurgeMultiplier(request.getSurgeMultiplier() != null ? request.getSurgeMultiplier() : DEFAULT_SURGE_MULTIPLIER);
                    existing.setSurgeStartTime(request.getSurgeStartTime());
                    existing.setSurgeEndTime(request.getSurgeEndTime());
                    existing.setOvertimePenaltyMultiplier(request.getOvertimePenaltyMultiplier() != null
                            ? request.getOvertimePenaltyMultiplier() : DEFAULT_OVERTIME_PENALTY_MULTIPLIER);
                    return pricingRuleRepository.save(existing);
                })
                .orElseGet(() -> pricingRuleRepository.save(PricingRule.builder()
                        .parkingLot(lot)
                        .vehicleType(request.getVehicleType())
                        .basePricePerHour(request.getBasePricePerHour())
                        .surgeMultiplier(request.getSurgeMultiplier() != null ? request.getSurgeMultiplier() : DEFAULT_SURGE_MULTIPLIER)
                        .surgeStartTime(request.getSurgeStartTime())
                        .surgeEndTime(request.getSurgeEndTime())
                        .overtimePenaltyMultiplier(request.getOvertimePenaltyMultiplier() != null
                                ? request.getOvertimePenaltyMultiplier() : DEFAULT_OVERTIME_PENALTY_MULTIPLIER)
                        .build()));

        return PricingRuleDto.fromEntity(rule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotDto> getSlots(Long parkingLotId) {
        return slotRepository.findByParkingLotId(parkingLotId).stream()
                .map(SlotDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PricingRuleDto> getPricingRules(Long parkingLotId) {
        return pricingRuleRepository.findByParkingLotId(parkingLotId).stream()
                .map(PricingRuleDto::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public SlotHoldResponse holdSlot(Long parkingLotId, VehicleTypeEnum vehicleType, Long bookingId) {
        Slot slot = slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, vehicleType)
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_AVAILABLE, "Không tìm thấy cấu hình chỗ đỗ cho loại xe này"));

        ParkingLot lot = slot.getParkingLot();
        if (lot.getStatus() != ParkingLotStatusEnum.APPROVED)
            throw new AppException(ErrorCode.BAD_REQUEST, "Bãi đỗ chưa được duyệt hoặc đã ngừng nhận xe");
        PricingRule price = pricingRuleRepository.findByParkingLotIdAndVehicleType(parkingLotId, vehicleType)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Bãi chưa có bảng giá cho loại xe này"));
        if (price.getBasePricePerHour() == null || price.getBasePricePerHour().signum() <= 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Bảng giá không hợp lệ");
        var previous = slotHoldRepository.findById(bookingId);
        if (previous.isPresent() && (!previous.get().getSlotId().equals(slot.getId()) || previous.get().isReleased()))
            throw new AppException(ErrorCode.CONFLICT, "Mã giữ chỗ đã được sử dụng hoặc hết hiệu lực");
        if (previous.isEmpty()) {
            if (slot.getAvailableCapacity() <= 0)
                return SlotHoldResponse.builder().success(false).remainingCapacity(0).message("Hết chỗ trống cho loại xe này").build();
            slot.setAvailableCapacity(slot.getAvailableCapacity() - 1);
            slotRepository.save(slot);
            slotHoldRepository.save(com.smartparking.parkinglot.domain.model.SlotHold.builder()
                    .bookingId(bookingId).slotId(slot.getId()).released(false).build());
        }
        BigDecimal basePrice = price.getBasePricePerHour();

        log.info("Held slot {} in lot {} for booking {}. Remaining capacity: {}",
                slot.getId(), parkingLotId, bookingId, slot.getAvailableCapacity());

        return SlotHoldResponse.builder()
                .success(true)
                .slotId(slot.getId())
                .parkingLotId(parkingLotId)
                .vehicleType(vehicleType)
                .remainingCapacity(slot.getAvailableCapacity())
                .baseHourlyPrice(basePrice)
                .merchantId(lot.getMerchantId()).parkingLotName(lot.getName()).parkingLotAddress(lot.getAddress())
                .surgeMultiplier(price.getSurgeMultiplier()).surgeStartTime(price.getSurgeStartTime())
                .surgeEndTime(price.getSurgeEndTime()).overtimePenaltyMultiplier(price.getOvertimePenaltyMultiplier())
                .message("Giữ chỗ thành công")
                .build();
    }

    @Override
    @Transactional
    public void releaseSlot(Long parkingLotId, VehicleTypeEnum vehicleType, Long bookingId) {
        Slot slot = slotRepository.findByParkingLotIdAndVehicleTypeForUpdate(parkingLotId, vehicleType)
                .orElseThrow(() -> new AppException(ErrorCode.SLOT_NOT_AVAILABLE, "Không tìm thấy chỗ đỗ"));
        var hold = slotHoldRepository.findById(bookingId);
        if (hold.isEmpty()) {
            log.warn("No slot hold ledger found for booking {}: reconciliation required", bookingId);
            throw new AppException(ErrorCode.CONFLICT, "Booking cũ thiếu sổ giữ chỗ; cần đối soát trước khi nhả slot");
        }
        if (!hold.get().getSlotId().equals(slot.getId()))
            throw new AppException(ErrorCode.CONFLICT, "Mã giữ chỗ không thuộc bãi hoặc loại xe này");
        if (hold.get().isReleased()) return;
        if (slot.getAvailableCapacity() >= slot.getTotalCapacity())
            throw new AppException(ErrorCode.CONFLICT, "Sức chứa không khớp sổ giữ chỗ; cần đối soát");
        slot.setAvailableCapacity(slot.getAvailableCapacity() + 1);
        hold.get().setReleased(true);
        slotRepository.save(slot);
        slotHoldRepository.save(hold.get());
    }

    private ParkingLot getOwnedParkingLot(Long merchantUserId, Long parkingLotId) {
        ParkingLot lot = parkingLotRepository.findById(parkingLotId)
                .orElseThrow(() -> new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ không tồn tại"));
        if (!lot.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền cấu hình bãi đỗ này!");
        }
        return lot;
    }

    private void assertLotApprovedForConfig(ParkingLot lot) {
        if (lot.getStatus() == ParkingLotStatusEnum.REJECTED || lot.getStatus() == ParkingLotStatusEnum.SUSPENDED) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    "Bãi đỗ đang bị từ chối hoặc tạm ngưng hoạt động, không thể cấu hình slot hoặc bảng giá. Trạng thái hiện tại: "
                            + lot.getStatus());
        }
    }
}
