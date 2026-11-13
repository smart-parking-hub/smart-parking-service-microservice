package com.smartparking.parkinglot.domain.repository;
import com.smartparking.parkinglot.domain.model.SlotHold;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SlotHoldRepository extends JpaRepository<SlotHold, Long> {
    java.util.List<SlotHold> findTop100ByReleasedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(java.time.LocalDateTime before);
    long countBySlotIdAndReleasedFalseAndPhysicalSlotIdIsNull(Long slotId);
    long countByPhysicalSlotIdAndReleasedFalse(String physicalSlotId);
    boolean existsByPhysicalSlotId(String physicalSlotId);
}
