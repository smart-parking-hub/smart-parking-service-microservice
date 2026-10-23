package com.smartparking.parkinglot.domain.repository;

import com.smartparking.common.enums.ParkingLotStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.parkinglot.domain.model.ParkingLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {
    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ParkingLot p WHERE p.id = :id")
    Optional<ParkingLot> findByIdForUpdate(@Param("id") Long id);

    Page<ParkingLot> findByStatus(ParkingLotStatusEnum status, Pageable pageable);
    long countByStatus(ParkingLotStatusEnum status);
    long countByMerchantIdAndStatus(Long merchantId, ParkingLotStatusEnum status);

    @Query("""
            SELECT p FROM ParkingLot p
            WHERE p.merchantId = :merchantId
              AND (:status IS NULL OR p.status = :status)
              AND (:keyword IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.address) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<ParkingLot> searchOwnedParkingLots(
            @Param("merchantId") Long merchantId,
            @Param("status") ParkingLotStatusEnum status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("SELECT p FROM ParkingLot p WHERE p.status = 'APPROVED' AND EXISTS (" +
           "SELECT s FROM Slot s WHERE s.parkingLot = p AND s.vehicleType = :vehicleType " +
           "AND (:includeFull = true OR s.availableCapacity > 0))")
    Page<ParkingLot> searchApprovedByVehicleType(
            @Param("vehicleType") VehicleTypeEnum vehicleType,
            @Param("includeFull") boolean includeFull,
            Pageable pageable);

    @Query("SELECT p FROM ParkingLot p WHERE p.status = 'APPROVED' AND EXISTS (" +
           "SELECT s FROM Slot s WHERE s.parkingLot = p AND s.vehicleType = :vehicleType " +
           "AND (:includeFull = true OR s.availableCapacity > 0))")
    List<ParkingLot> findApprovedByVehicleType(
            @Param("vehicleType") VehicleTypeEnum vehicleType,
            @Param("includeFull") boolean includeFull);
}
