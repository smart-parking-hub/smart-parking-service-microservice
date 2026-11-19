package com.smartparking.parkinglot;

import com.smartparking.common.enums.*;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.parkinglot.application.dto.ScheduledHoldRequest;
import com.smartparking.parkinglot.application.service.ScheduledSlotService;
import com.smartparking.parkinglot.domain.model.*;
import com.smartparking.parkinglot.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static com.smartparking.common.util.BookingSchedulePolicy.VERSION;
import static com.smartparking.common.util.BookingSchedulePolicy.ZONE;
import static org.junit.jupiter.api.Assertions.*;

@org.springframework.test.context.ActiveProfiles("test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(properties = {"app.schedule.reconciliation.enabled=false", "spring.kafka.listener.auto-startup=false", "app.parking-layout.physical-booking-enabled=true", "app.parking-layout.storage-provider=mock"})
class ScheduledReservationIntegrationTest {
    @Autowired ScheduledSlotService service;
    @Autowired ParkingLotRepository lots;
    @Autowired SlotRepository groups;
    @Autowired PricingRuleRepository prices;
    @Autowired ParkingLayoutRepository layouts;
    @Autowired PhysicalParkingSlotRepository physical;
    @Autowired SlotReservationRepository reservations;
    @Autowired SlotHoldRepository legacy;
    @Autowired com.smartparking.parkinglot.application.service.LegacyScheduleImport imports;
    @Autowired CurrentSlotAllocationRepository allocations;
    @Autowired org.springframework.test.web.servlet.MockMvc http;
    private static final AtomicLong IDS = new AtomicLong(9000000);

    // Opt-in against an isolated MySQL schema, never the application's existing database.
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("SCHEDULE_TEST_JDBC_URL");
        if (url != null) {
            if (!url.matches("jdbc:mysql://[^/]+/sph_schedule_test_[a-zA-Z0-9_]+(?:\\?.*)?"))
                throw new IllegalArgumentException("Only isolated sph_schedule_test_* databases are accepted");
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
            registry.add("spring.datasource.username", () -> System.getenv("SCHEDULE_TEST_DB_USER"));
            registry.add("spring.datasource.password", () -> System.getenv("SCHEDULE_TEST_DB_PASSWORD"));
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
            registry.add("spring.flyway.enabled", () -> "true");
            registry.add("spring.sql.init.mode", () -> "never");
        }
    }

    private record Yard(Long id, Slot group, List<String> physicalIds) {}
    private LocalDateTime time(int hour, int minute) { return LocalDateTime.now(ZONE).plusDays(2).withHour(hour).withMinute(minute).withSecond(0).withNano(0); }
    private Yard yard(int capacity, boolean withPhysical) {
        var lot = lots.saveAndFlush(ParkingLot.builder().merchantId(999L).name("Schedule " + UUID.randomUUID())
                .address("Test only").latitude(BigDecimal.ONE).longitude(BigDecimal.ONE).status(ParkingLotStatusEnum.APPROVED).build());
        var group = groups.saveAndFlush(Slot.builder().parkingLot(lot).vehicleType(VehicleTypeEnum.CONTAINER)
                .totalCapacity(capacity).availableCapacity(capacity).build());
        prices.saveAndFlush(PricingRule.builder().parkingLot(lot).vehicleType(VehicleTypeEnum.CONTAINER)
                .basePricePerHour(new BigDecimal("20000")).surgeMultiplier(BigDecimal.ONE).overtimePenaltyMultiplier(BigDecimal.ONE).build());
        List<String> ids = new ArrayList<>();
        if (withPhysical) {
            layouts.saveAndFlush(ParkingLayout.builder().parkingLot(lot).activePublicationId(UUID.randomUUID().toString())
                    .layoutVersion(1).allocationMode(AllocationMode.PHYSICAL).build());
            for (int i = 0; i < capacity; i++) {
                String id = UUID.randomUUID().toString(); ids.add(id);
                physical.saveAndFlush(PhysicalParkingSlot.builder().id(id).parkingLotId(lot.getId()).slotGroupId(group.getId())
                        .vehicleType(VehicleTypeEnum.CONTAINER).code("A"+i).normalizedCode("a"+i).status("AVAILABLE").build());
            }
        }
        return new Yard(lot.getId(), group, ids);
    }
    private Long hold(Yard yard, String id, LocalDateTime from, LocalDateTime until) {
        long booking = IDS.incrementAndGet();
        service.hold(yard.id(), new ScheduledHoldRequest(booking, VehicleTypeEnum.CONTAINER, id, from, until, LocalDateTime.now(ZONE).plusMinutes(15), VERSION));
        return booking;
    }
    private int available(Yard yard, int fromHour, int fromMinute, int toHour, int toMinute) {
        return service.available(yard.id(), VehicleTypeEnum.CONTAINER, time(fromHour,fromMinute),time(toHour,toMinute),null).remainingCapacity();
    }

    @Test void futureBookingDoesNotBlockMorningAndEnforcesExactlyThirtyMinutesOnBothSides() {
        var yard = yard(1,true); hold(yard,yard.physicalIds().getFirst(),time(13,0),time(15,0));
        assertEquals(1,available(yard,8,0,12,0));
        assertEquals(1,available(yard,11,30,12,30));
        assertEquals(0,available(yard,12,0,13,0));
        assertEquals(0,available(yard,14,0,16,0));
        assertEquals(0,available(yard,15,0,16,0));
        assertEquals(1,available(yard,15,30,16,0));
        hold(yard,yard.physicalIds().getFirst(),time(8,0),time(12,0));
        assertEquals(2,reservations.findBySlotGroupIdAndStateNot(yard.group().getId(),"RELEASED").size());
        assertEquals(1,groups.findById(yard.group().getId()).orElseThrow().getAvailableCapacity());
    }
    @Test void rolloutKeepsReadAndWriteOnTheSamePolicyAndPreservesExistingHolds() {
        var yard = yard(1, true);
        var slot = yard.physicalIds().getFirst();
        var start = time(13, 0);
        var end = time(15, 0);
        var expires = LocalDateTime.now(ZONE).plusMinutes(15);
        var legacy = new ScheduledHoldRequest(IDS.incrementAndGet(), VehicleTypeEnum.CONTAINER, slot, start, end, expires, null);
        var adjacent = new ScheduledHoldRequest(IDS.incrementAndGet(), VehicleTypeEnum.CONTAINER, slot,
                time(15, 30), time(16, 0), expires, VERSION);

        ReflectionTestUtils.setField(service, "earlyArrivalEnabled", false);
        try {
            assertEquals(1, service.hold(yard.id(), legacy).getSchedulePolicyVersion());
            var availability = service.available(yard.id(), VehicleTypeEnum.CONTAINER, adjacent.startTime(), adjacent.endTime(), slot);
            assertEquals(1, availability.remainingCapacity());
            assertEquals(0, availability.checkInEarlyMinutes());
            assertEquals(30, availability.turnaroundMinutes());
            var timeline = service.timeline(yard.id(), VehicleTypeEnum.CONTAINER, adjacent.startTime().toLocalDate(), slot);
            assertEquals(0, timeline.checkInEarlyMinutes());
            assertTrue(timeline.blocks().stream().anyMatch(block -> block.start().equals(adjacent.startTime())
                    && block.remainingCapacity() == 1 && block.status().equals("AVAILABLE")));
            assertEquals(1, service.hold(yard.id(), adjacent).getSchedulePolicyVersion());
            service.release(yard.id(), adjacent.bookingId());
        } finally {
            ReflectionTestUtils.setField(service, "earlyArrivalEnabled", true);
        }

        var stored = reservations.findById(legacy.bookingId()).orElseThrow();
        assertEquals(start, stored.getProtectedStart());
        assertEquals(end.plusMinutes(30), stored.getProtectedUntil());
        assertEquals(1, service.hold(yard.id(), legacy).getSchedulePolicyVersion());
        assertEquals(0, available(yard, 15, 30, 16, 0));
        assertThrows(AppException.class, () -> hold(yard, slot, time(15, 30), time(16, 0)));

        var afterBuffer = new ScheduledHoldRequest(IDS.incrementAndGet(), VehicleTypeEnum.CONTAINER, slot,
                time(16, 0), time(16, 30), expires, null);
        assertEquals(1, available(yard, 16, 0, 16, 30));
        var error = assertThrows(AppException.class, () -> service.hold(yard.id(), afterBuffer));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertNotNull(hold(yard, slot, time(16, 0), time(16, 30)));
    }

    @Test void earlyArrivalWaitsForTheLateVehiclesActualCooldown() {
        var yard = yard(1, true);
        String slot = yard.physicalIds().getFirst();
        long previousId = hold(yard, slot, time(13, 0), time(15, 0));
        long nextId = hold(yard, slot, time(15, 30), time(16, 30));
        var now = LocalDateTime.now(ZONE).withNano(0);

        var previous = reservations.findById(previousId).orElseThrow();
        previous.setStartTime(now.minusHours(2));
        previous.setProtectedStart(previous.getStartTime().minusMinutes(15));
        previous.setEndTime(now.minusMinutes(20));
        previous.setProtectedUntil(previous.getEndTime().plusMinutes(15));
        previous.setState("OCCUPIED");
        reservations.saveAndFlush(previous);
        var group = groups.findById(yard.group().getId()).orElseThrow();
        group.setAvailableCapacity(0);
        groups.saveAndFlush(group);

        var next = reservations.findById(nextId).orElseThrow();
        next.setStartTime(now.plusMinutes(10));
        next.setProtectedStart(next.getStartTime().minusMinutes(15));
        next.setEndTime(now.plusHours(1));
        next.setProtectedUntil(next.getEndTime().plusMinutes(15));
        reservations.saveAndFlush(next);
        service.confirm(yard.id(), nextId);
        assertThrows(AppException.class, () -> service.occupy(yard.id(), nextId, "while-occupied"));

        var actualCheckout = now.minusMinutes(2);
        service.complete(yard.id(), previousId, actualCheckout);
        var cooldown = reservations.findById(previousId).orElseThrow();
        assertEquals(actualCheckout.plusMinutes(15), cooldown.getProtectedUntil());
        assertThrows(AppException.class, () -> service.occupy(yard.id(), nextId, "during-cooldown"));

        cooldown.setProtectedUntil(now.minusSeconds(1));
        reservations.saveAndFlush(cooldown);
        service.occupy(yard.id(), nextId, "early-after-cooldown");
        assertEquals("OCCUPIED", reservations.findById(nextId).orElseThrow().getState());
    }

    @Test void capacityUsesPeakConcurrencyRatherThanCountOfAllIntersectingBookings() {
        var yard=yard(2,false); hold(yard,null,time(8,0),time(9,0)); hold(yard,null,time(10,0),time(11,0));
        assertEquals(1,available(yard,8,0,11,0)); hold(yard,null,time(8,0),time(11,0));
        assertEquals(0,available(yard,8,0,11,0));
    }
    @Test void requiresOnePhysicalSlotThroughoutRatherThanCombiningFreeFragments() {
        var yard=yard(2,true); hold(yard,yard.physicalIds().get(0),time(8,0),time(9,0));
        hold(yard,yard.physicalIds().get(1),time(10,0),time(11,0));
        assertEquals(0,available(yard,8,0,11,0));
    }
    @Test void retryAndCancellationAreIdempotentAndCannotReviveReleasedReservation() {
        var yard=yard(1,false); long id=hold(yard,null,time(13,0),time(15,0));
        var request=new ScheduledHoldRequest(id,VehicleTypeEnum.CONTAINER,null,time(13,0),time(15,0),LocalDateTime.now(ZONE).plusMinutes(15),VERSION);
        assertTrue(service.hold(yard.id(),request).isSuccess());
        assertThrows(AppException.class,()->service.hold(yard.id(),new ScheduledHoldRequest(id,VehicleTypeEnum.CONTAINER,null,time(14,0),time(15,0),request.holdExpiresAt(),VERSION)));
        service.release(yard.id(),id); service.release(yard.id(),id);
        assertEquals(1,available(yard,13,0,15,0));
        assertThrows(AppException.class,()->service.hold(yard.id(),request));
        assertThrows(AppException.class,()->service.confirm(yard.id(),id));
    }
    @Test void expiredPaymentDeadlineDoesNotAutomaticallySellAPossiblyPaidHold() {
        var yard=yard(1,false); long id=hold(yard,null,time(13,0),time(15,0));
        var booking=reservations.findById(id).orElseThrow(); booking.setHoldExpiresAt(LocalDateTime.now(ZONE).minusMinutes(1)); reservations.saveAndFlush(booking);
        assertEquals(0,available(yard,13,0,15,0));
    }
    @Test void legacyHoldAndCapacityDeficitAreConservativelyUnavailable() {
        var yard=yard(1,false); legacy.saveAndFlush(SlotHold.builder().bookingId(IDS.incrementAndGet()).slotId(yard.group().getId()).released(false).build());
        assertEquals(0,available(yard,13,0,15,0));
    }
    @Test void suspendingALotBlocksNewBookingsButDoesNotPreventCancellation() {
        var yard=yard(1,true); long id=hold(yard,null,time(13,0),time(15,0));
        var lot=lots.findById(yard.id()).orElseThrow();lot.setStatus(ParkingLotStatusEnum.SUSPENDED);lots.saveAndFlush(lot);
        assertThrows(AppException.class,()->hold(yard,null,time(8,0),time(12,0)));
        service.release(yard.id(),id);
        assertEquals("RELEASED",reservations.findById(id).orElseThrow().getState());
    }
    @Test void cannotShrinkOrRepublishLayoutWhilePromisedTimeIsOutstanding() {
        var yard=yard(2,true); hold(yard,null,time(13,0),time(15,0));
        assertThrows(AppException.class,()->service.assertConfigurationSafe(yard.group().getId(),1));
        assertThrows(AppException.class,()->service.assertLayoutSafe(yard.id()));
    }
    @Test void bufferAndTimelineCrossMidnightWithoutLosingThePreviousDayBooking() {
        var yard=yard(1,true); var from=time(23,30); hold(yard,null,from,from.plusHours(1));
        var next=service.timeline(yard.id(),VehicleTypeEnum.CONTAINER,from.toLocalDate().plusDays(1),null);
        assertEquals(30, next.bufferMinutes());
        assertEquals(15, next.checkInEarlyMinutes());
        assertEquals(15, next.turnaroundMinutes());
        assertEquals("FULL",next.blocks().getFirst().status());
        assertEquals("BUFFER",next.blocks().get(1).status());
        assertEquals("AVAILABLE",next.blocks().get(2).status());
    }
    @Test void concurrentRequestsCanOnlySellTheSamePhysicalWindowOnce() throws Exception {
        var yard=yard(1,true); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt=()->{start.await();try {hold(yard,null,time(13,0),time(15,0));return true;} catch(AppException expected){return false;}};
            var a=pool.submit(attempt);var b=pool.submit(attempt);start.countDown();
            assertNotEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }
    }
    @Test void overdueOccupiedVehicleIsNeverFreedByExpectedDeparture() {
        var yard=yard(1,true); var start=LocalDateTime.now(ZONE).minusHours(2).withMinute(0).withSecond(0).withNano(0);
        reservations.saveAndFlush(SlotReservation.builder().bookingId(IDS.incrementAndGet()).parkingLotId(yard.id()).slotGroupId(yard.group().getId())
                .physicalSlotId(yard.physicalIds().getFirst()).startTime(start).endTime(start.plusHours(1)).protectedStart(start)
                .protectedUntil(start.plusHours(1).plusMinutes(30)).state("OCCUPIED").policyVersion(1).updatedAt(start).build());
        assertEquals(0,available(yard,13,0,15,0));
    }

    @Test void actualOccupancyAndCooldownAreSeparateAndOldCompensationCannotAbortANewerAttempt() {
        var yard=yard(1,true); long id=hold(yard,null,time(13,0),time(15,0));
        service.confirm(yard.id(),id);
        assertThrows(AppException.class,()->service.occupy(yard.id(),id,"too-early"));
        var current=LocalDateTime.now(ZONE).withSecond(0).withNano(0);
        var start=current.minusMinutes(current.getMinute()%30);
        var booking=reservations.findById(id).orElseThrow();
        booking.setStartTime(start); booking.setEndTime(start.plusMinutes(30)); booking.setProtectedStart(start.minusMinutes(15));
        booking.setProtectedUntil(start.plusMinutes(45)); reservations.saveAndFlush(booking);
        service.occupy(yard.id(),id,"attempt-1"); service.occupy(yard.id(),id,"attempt-2");
        service.abortOccupation(yard.id(),id,"attempt-1");
        assertEquals("OCCUPIED",reservations.findById(id).orElseThrow().getState());
        assertEquals(0,groups.findById(yard.group().getId()).orElseThrow().getAvailableCapacity());
        assertThrows(AppException.class,()->service.release(yard.id(),id));
        var checkout=LocalDateTime.now(ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var lot=lots.findById(yard.id()).orElseThrow();lot.setStatus(ParkingLotStatusEnum.SUSPENDED);lots.saveAndFlush(lot);
        service.complete(yard.id(),id,checkout); service.complete(yard.id(),id,checkout); service.release(yard.id(),id);
        assertEquals("COOLDOWN",reservations.findById(id).orElseThrow().getState());
        assertEquals(1,groups.findById(yard.group().getId()).orElseThrow().getAvailableCapacity());
        assertTrue(allocations.findByBookingId(id).isEmpty());
        lot.setStatus(ParkingLotStatusEnum.APPROVED);lots.saveAndFlush(lot);
        assertEquals(0,service.available(yard.id(),VehicleTypeEnum.CONTAINER,start.plusMinutes(30),start.plusHours(1),null).remainingCapacity());
        assertEquals(1,service.available(yard.id(),VehicleTypeEnum.CONTAINER,start.plusHours(1),start.plusMinutes(90),null).remainingCapacity());
    }

    @Test void legacyImportPreservesOddMinutesAndEarlyEntryWithoutFreeingTheOldHoldBeforeActivation() {
        var yard=yard(1,true); long id=IDS.incrementAndGet();
        yard.group().setAvailableCapacity(0);groups.saveAndFlush(yard.group());
        String physicalId=yard.physicalIds().getFirst();
        legacy.saveAndFlush(SlotHold.builder().bookingId(id).slotId(yard.group().getId()).physicalSlotId(physicalId).released(false).build());
        allocations.saveAndFlush(CurrentSlotAllocation.builder().parkingLotId(yard.id()).slotGroupId(yard.group().getId()).physicalSlotId(physicalId).bookingId(id).build());
        var snapshot=new com.smartparking.parkinglot.application.service.LegacyScheduleImport.Snapshot(id,VehicleTypeEnum.CONTAINER,physicalId,
                time(13,10),time(15,20),time(14,10),null,null,"PENDING_CHECKIN",30);
        imports.prepare(yard.id(),snapshot);
        assertEquals(0,available(yard,8,0,12,0));
        assertFalse(legacy.findById(id).orElseThrow().isReleased());
        imports.activate(yard.id(),id); imports.activate(yard.id(),id);
        assertTrue(legacy.findById(id).orElseThrow().isReleased());
        assertTrue(allocations.findByBookingId(id).isEmpty());
        assertEquals(1,groups.findById(yard.group().getId()).orElseThrow().getAvailableCapacity());
        assertEquals(1,available(yard,8,0,12,0));
        assertEquals(0,available(yard,12,30,13,0));
        assertEquals(0,available(yard,15,30,16,0));
        assertEquals(0,available(yard,16,0,17,0));
        assertEquals(1,available(yard,16,30,17,0));
        var adopted = reservations.findById(id).orElseThrow();
        assertEquals(time(13,10),adopted.getStartTime());
        assertEquals(time(12,40),adopted.getProtectedStart());
        assertEquals(time(15,50),adopted.getProtectedUntil());
    }

    @Test void publicAvailabilityIsAnonymousAndPrivateMutationsRequireTheServiceToken() throws Exception {
        var yard=yard(1,true);hold(yard,null,time(13,0),time(15,0));
        var result=http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/parking-lots/{id}/availability",yard.id())
                .param("vehicleType","CONTAINER").param("start",time(8,0).toString()).param("end",time(12,0).toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.remainingCapacity").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.bufferMinutes").value(30))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.checkInEarlyMinutes").value(15))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.turnaroundMinutes").value(15))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("bookingId"));
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/parking-lots/{id}/slots/reservations/123/confirm",yard.id()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        http.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/parking-lots/{id}/availability",yard.id())
                .param("vehicleType","CONTAINER").param("start",time(8,15).toString()).param("end",time(12,0).toString()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }
}
