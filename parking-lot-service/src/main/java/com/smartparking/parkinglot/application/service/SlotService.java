package com.smartparking.parkinglot.application.service;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.application.dto.*;

import java.util.List;

public interface SlotService {
    SlotDto configureSlot(Long merchantUserId, Long parkingLotId, SlotConfigRequest request);
    List<SlotDto> configureSlots(Long merchantUserId, Long parkingLotId, List<SlotConfigRequest> requests);
    PricingRuleDto configurePricing(Long merchantUserId, Long parkingLotId, PricingRuleRequest request);
    List<PricingRuleDto> configurePricingRules(Long merchantUserId, Long parkingLotId, List<PricingRuleRequest> requests);
    List<SlotDto> getSlots(Long parkingLotId);
    List<PricingRuleDto> getPricingRules(Long parkingLotId);

    // RPC methods for BookingService
    SlotHoldResponse holdSlot(Long parkingLotId, VehicleTypeEnum vehicleType, Long bookingId);
    SlotHoldResponse holdSlot(Long parkingLotId, VehicleTypeEnum vehicleType, Long bookingId, String physicalSlotId);
    void releaseSlot(Long parkingLotId, VehicleTypeEnum vehicleType, Long bookingId);
}
