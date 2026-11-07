package com.smartparking.parkinglot.infrastructure.scheduler;
import com.smartparking.parkinglot.domain.repository.SlotHoldRepository;
import com.smartparking.parkinglot.domain.repository.SlotRepository;
import com.smartparking.parkinglot.application.service.SlotService;
import com.smartparking.parkinglot.infrastructure.client.BookingHoldVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
@Component @RequiredArgsConstructor @Slf4j
public class SlotHoldReconciliation {
    private final SlotHoldRepository holds;
    private final SlotRepository slots;
    private final SlotService service;
    private final BookingHoldVerifier bookingVerifier;
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void reconcile() {
        for (var hold : holds.findTop100ByReleasedFalseAndCreatedAtBeforeOrderByCreatedAtAsc(LocalDateTime.now().minusMinutes(20))) {
            try {
                if (!bookingVerifier.canRelease(hold.getBookingId())) continue;
                slots.findById(hold.getSlotId()).ifPresent(slot -> service.releaseSlot(slot.getParkingLot().getId(),slot.getVehicleType(),hold.getBookingId()));
            } catch (Exception failure) {
                log.warn("Hold reconciliation deferred for booking {}",hold.getBookingId());
            }
        }
    }
}
