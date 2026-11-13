package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.CurrentSlotAllocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrentSlotAllocationRepository extends JpaRepository<CurrentSlotAllocation, Long> {

    Optional<CurrentSlotAllocation> findByPhysicalSlotId(String physicalSlotId);

    Optional<CurrentSlotAllocation> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT csa FROM CurrentSlotAllocation csa WHERE csa.physicalSlotId = :physicalSlotId")
    Optional<CurrentSlotAllocation> findByPhysicalSlotIdForUpdate(@Param("physicalSlotId") String physicalSlotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT csa FROM CurrentSlotAllocation csa WHERE csa.bookingId = :bookingId")
    Optional<CurrentSlotAllocation> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    List<CurrentSlotAllocation> findByParkingLotId(Long parkingLotId);

    void deleteByBookingId(Long bookingId);
}
