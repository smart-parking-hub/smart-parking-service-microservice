package com.smartparking.parkinglot.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Public projections intentionally omit booking IDs, plates and driver identities. */
public record ScheduleAvailability(String timezone, int blockMinutes, int bufferMinutes, String allocationMode,
        LocalDateTime updatedAt, int remainingCapacity, List<SlotOption> slots, List<TimeBlock> blocks) {
    public record SlotOption(String id, String code) {}
    public record TimeBlock(LocalDateTime start, LocalDateTime end, int remainingCapacity, String status) {}
}
