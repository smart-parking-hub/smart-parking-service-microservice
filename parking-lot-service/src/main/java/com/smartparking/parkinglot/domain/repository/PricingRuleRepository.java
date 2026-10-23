package com.smartparking.parkinglot.domain.repository;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Long> {
    Optional<PricingRule> findByParkingLotIdAndVehicleType(Long parkingLotId, VehicleTypeEnum vehicleType);
    List<PricingRule> findByParkingLotId(Long parkingLotId);
}
