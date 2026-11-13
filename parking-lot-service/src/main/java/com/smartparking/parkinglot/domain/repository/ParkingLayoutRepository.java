package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.ParkingLayout;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParkingLayoutRepository extends JpaRepository<ParkingLayout, Long> {

    Optional<ParkingLayout> findByParkingLotId(Long parkingLotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pl FROM ParkingLayout pl WHERE pl.parkingLot.id = :lotId")
    Optional<ParkingLayout> findByParkingLotIdForUpdate(@Param("lotId") Long lotId);
}
