package com.smartparking.parkinglot.domain.repository;

import com.smartparking.parkinglot.domain.model.SlotReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SlotReservationRepository extends JpaRepository<SlotReservation, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SlotReservation r WHERE r.bookingId = :id")
    java.util.Optional<SlotReservation> findByIdForUpdate(@Param("id") Long id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SlotReservation r WHERE r.slotGroupId = :groupId AND r.state <> 'RELEASED'")
    List<SlotReservation> findActiveForUpdate(@Param("groupId") Long groupId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SlotReservation r WHERE r.parkingLotId = :lotId AND r.state <> 'RELEASED' AND (r.protectedUntil > :now OR r.state = 'OCCUPIED')")
    List<SlotReservation> findLotActiveForUpdate(@Param("lotId") Long lotId, @Param("now") LocalDateTime now);
    long countBySlotGroupIdAndState(Long groupId, String state);
    @Query("SELECT r.slotGroupId FROM SlotReservation r WHERE r.bookingId = :id")
    java.util.Optional<Long> findGroupId(@Param("id") Long bookingId);
    List<SlotReservation> findTop100ByStateInAndBookingIdGreaterThanOrderByBookingIdAsc(java.util.Collection<String> states, Long after);
    // OCCUPIED is retained even after the expected exit, until an authoritative checkout arrives.
    @Query("SELECT r FROM SlotReservation r WHERE r.slotGroupId = :groupId AND r.state <> 'RELEASED' "
            + "AND (r.protectedUntil > :from OR r.state = 'OCCUPIED') AND r.protectedStart < :until")
    List<SlotReservation> findBlocking(@Param("groupId") Long groupId, @Param("from") LocalDateTime from,
                                      @Param("until") LocalDateTime until);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SlotReservation r WHERE r.slotGroupId = :groupId AND r.state IN ('OCCUPIED', 'COOLDOWN') "
            + "AND (r.protectedUntil > :entry OR r.state = 'OCCUPIED') AND r.protectedStart <= :entry")
    List<SlotReservation> findBlockingAtForUpdate(@Param("groupId") Long groupId, @Param("entry") LocalDateTime entry);

    List<SlotReservation> findBySlotGroupIdAndStateNot(Long groupId, String state);
    boolean existsByParkingLotIdAndStateNotAndProtectedUntilAfter(Long lotId, String state, LocalDateTime now);
}
