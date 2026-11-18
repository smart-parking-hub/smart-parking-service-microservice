package com.smartparking.parkinglot.domain.repository;

import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    @Query("SELECT s.vehicleType FROM Slot s WHERE s.id = :id")
    Optional<VehicleTypeEnum> findVehicleType(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.parkingLot.id = :parkingLotId AND s.vehicleType = :vehicleType")
    Optional<Slot> findByParkingLotIdAndVehicleTypeForUpdate(
            @Param("parkingLotId") Long parkingLotId,
            @Param("vehicleType") VehicleTypeEnum vehicleType);

    Optional<Slot> findByParkingLotIdAndVehicleType(Long parkingLotId, VehicleTypeEnum vehicleType);

    List<Slot> findByParkingLotId(Long parkingLotId);
}
