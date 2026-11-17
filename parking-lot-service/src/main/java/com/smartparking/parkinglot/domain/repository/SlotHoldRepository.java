package com.smartparking.parkinglot.domain.repository;
import com.smartparking.parkinglot.domain.model.SlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT h FROM SlotHold h WHERE h.bookingId = :id")
    java.util.Optional<SlotHold> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    java.util.List<SlotHold> findBySlotIdAndReleasedFalse(Long slotId);
    java.util.List<SlotHold> findTop100ByReleasedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(java.time.LocalDateTime before);
    long countBySlotIdAndReleasedFalseAndPhysicalSlotIdIsNull(Long slotId);
    long countByPhysicalSlotIdAndReleasedFalse(String physicalSlotId);
    boolean existsByPhysicalSlotId(String physicalSlotId);
}
