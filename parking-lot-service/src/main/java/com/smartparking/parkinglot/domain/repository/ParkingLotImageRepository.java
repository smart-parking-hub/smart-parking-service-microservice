package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.ParkingLotImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingLotImageRepository extends JpaRepository<ParkingLotImage, Long> {
    long countByParkingLotId(Long parkingLotId);
    List<ParkingLotImage> findByParkingLotIdOrderByCoverDescCreatedAtAsc(Long parkingLotId);
    Optional<ParkingLotImage> findByIdAndParkingLotId(Long id, Long parkingLotId);
    Optional<ParkingLotImage> findFirstByParkingLotIdOrderByCreatedAtAsc(Long parkingLotId);
}
