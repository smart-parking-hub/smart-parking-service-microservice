package com.smartparking.booking.domain.repository;

import com.smartparking.common.enums.QueueStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.booking.domain.model.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueRepository extends JpaRepository<Queue, Long> {

    Optional<Queue> findByDriverIdAndStatus(Long driverId, QueueStatusEnum status);

    List<Queue> findByParkingLotIdAndVehicleTypeAndStatusOrderByJoinedAtAsc(
            Long parkingLotId,
            VehicleTypeEnum vehicleType,
            QueueStatusEnum status);
}
