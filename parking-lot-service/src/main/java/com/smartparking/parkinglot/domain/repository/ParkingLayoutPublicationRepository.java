package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.ParkingLayoutPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParkingLayoutPublicationRepository extends JpaRepository<ParkingLayoutPublication, String> {

    Optional<ParkingLayoutPublication> findByParkingLotIdAndRequestId(Long parkingLotId, String requestId);

    Optional<ParkingLayoutPublication> findByParkingLotIdAndId(Long parkingLotId, String id);
}
