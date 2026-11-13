package com.smartparking.parkinglot.domain.repository;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.PhysicalParkingSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhysicalParkingSlotRepository extends JpaRepository<PhysicalParkingSlot, String> {

    List<PhysicalParkingSlot> findByParkingLotId(Long parkingLotId);

    Optional<PhysicalParkingSlot> findByIdAndParkingLotId(String id, Long parkingLotId);

    List<PhysicalParkingSlot> findByParkingLotIdAndVehicleType(Long parkingLotId, VehicleTypeEnum vehicleType);

    Optional<PhysicalParkingSlot> findByParkingLotIdAndNormalizedCode(Long parkingLotId, String normalizedCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pps FROM PhysicalParkingSlot pps WHERE pps.id = :id AND pps.parkingLotId = :parkingLotId")
    Optional<PhysicalParkingSlot> findByIdAndParkingLotIdForUpdate(@Param("id") String id, @Param("parkingLotId") Long parkingLotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pps FROM PhysicalParkingSlot pps WHERE pps.parkingLotId = :parkingLotId AND pps.vehicleType = :vehicleType AND pps.status = 'AVAILABLE' AND pps.id NOT IN (SELECT csa.physicalSlotId FROM CurrentSlotAllocation csa WHERE csa.parkingLotId = :parkingLotId) ORDER BY pps.code ASC")
    List<PhysicalParkingSlot> findAvailableSlotsForUpdate(@Param("parkingLotId") Long parkingLotId, @Param("vehicleType") VehicleTypeEnum vehicleType);
}
