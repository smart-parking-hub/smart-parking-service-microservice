package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.ParkingLayoutAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ParkingLayoutAssetRepository extends JpaRepository<ParkingLayoutAsset, Long> {

    Optional<ParkingLayoutAsset> findByAssetId(String assetId);

    Optional<ParkingLayoutAsset> findByParkingLotIdAndAssetId(Long parkingLotId, String assetId);
}
