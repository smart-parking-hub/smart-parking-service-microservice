package com.smartparking.booking;

import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.OwnedVehicleClient;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.HoldPaymentAssessmentClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The vehicle schedule invariant also applies when optional unpaid-hold limits are disabled. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vehicle_schedule_it;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=15000",
        "spring.kafka.listener.auto-startup=false", "app.booking.hold-policy.enabled=false"
})
class VehicleBookingConflictIntegrationTest {
    @Autowired BookingService service;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;
    @MockBean OwnedVehicleClient vehicles;
    @MockBean ParkingLotClient lots;
    @MockBean OutboxPublisherService events;
    @MockBean QueueService queue;
    @MockBean HoldPaymentAssessmentClient assessments;
    @MockBean KafkaTemplate<String, Object> kafka;
    LocalDateTime now;

    @BeforeEach void setup() {
        bookings.deleteAll();
        jdbc.update("DELETE FROM booking_hold_policy_locks");
        now = LocalDateTime.now(BUSINESS_ZONE).withNano(0);
        when(vehicles.require(anyLong())).thenAnswer(call -> new OwnedVehicleClient.Vehicle(
                call.getArgument(0), "51C-482.91", VehicleTypeEnum.LIGHT_TRUCK, "ACTIVE"));
        when(lots.holdSlot(anyLong(), any())).thenReturn(ApiResponse.ok(SlotHoldResponse.builder()
                .success(true).merchantId(99L).baseHourlyPrice(new BigDecimal("20000")).build()));
    }

    private CreateBookingRequest request(int startHours, int endHours) {
        return CreateBookingRequest.builder().requestId(UUID.randomUUID().toString())
                .vehicleId(11L).parkingLotId(6L).vehicleType(VehicleTypeEnum.LIGHT_TRUCK)
                .expectedCheckInTime(now.plusHours(startHours)).expectedCheckOutTime(now.plusHours(endHours)).build();
    }

    private Booking existing(BookingStatusEnum status, int startHours, int endHours) {
        return bookings.saveAndFlush(Booking.builder().bookingCode("BK" + UUID.randomUUID())
                .driverId(7L).vehicleId(11L).parkingLotId(5L).merchantId(99L)
                .vehicleType(VehicleTypeEnum.LIGHT_TRUCK).status(status)
                .expectedCheckInTime(now.plusHours(startHours)).expectedCheckOutTime(now.plusHours(endHours))
                .expiresAt(now.plusHours(3)).build());
    }

    private void rejects(CreateBookingRequest intent) {
        var error = assertThrows(AppException.class, () -> service.createBooking(7L, intent));
        assertEquals(ErrorCode.CONFLICT, error.getErrorCode());
        assertTrue(error.getMessage().contains("trùng thời gian"));
        verify(lots, never()).holdSlot(anyLong(), any());
        assertEquals(1, bookings.count());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatusEnum.class, names = {
            "PENDING_PAYMENT", "PENDING_CHECKIN", "PARKING", "OVERTIME", "AWAITING_FINAL_PAYMENT"
    })
    void rejectsOverlappingVehicleAtAnotherLot(BookingStatusEnum status) {
        existing(status, 1, 4);
        rejects(request(2, 3));
    }

    @ParameterizedTest
    @CsvSource({"1,3", "3,5", "1,5", "2,4"})
    void rejectsEveryOverlapShape(int start, int end) {
        existing(BookingStatusEnum.PENDING_CHECKIN, 2, 4);
        rejects(request(start, end));
    }

    @Test void rejectsSameLotOverlapAsWell() {
        existing(BookingStatusEnum.PENDING_CHECKIN, 2, 4);
        var intent = request(2, 3); intent.setParkingLotId(5L);
        rejects(intent);
    }

    @ParameterizedTest
    @CsvSource({"1,2", "4,5"})
    void allowsAdjacentNonOverlappingReservations(int start, int end) {
        existing(BookingStatusEnum.PENDING_CHECKIN, 2, 4);
        assertNotNull(service.createBooking(7L, request(start, end)));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatusEnum.class, names = {"COMPLETED", "REVIEWED", "CANCELLED", "EXPIRED"})
    void closedBookingsDoNotBlockVehicle(BookingStatusEnum status) {
        existing(status, 1, 4);
        assertNotNull(service.createBooking(7L, request(2, 3)));
    }

    @Test void anotherVehicleCanBookTheSameTime() {
        existing(BookingStatusEnum.PARKING, -1, 4);
        var intent = request(2, 3); intent.setVehicleId(12L);
        assertNotNull(service.createBooking(7L, intent));
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatusEnum.class, names = {"PARKING", "OVERTIME", "AWAITING_FINAL_PAYMENT"})
    void overdueVehicleRemainsOccupiedUntilCheckoutCompletes(BookingStatusEnum status) {
        existing(status, -3, -1);
        rejects(request(1, 2));
    }

    @Test void earlyCheckinCountsAsOccupiedBeforeTheReservedStart() {
        var parked = existing(BookingStatusEnum.PARKING, 2, 4);
        parked.setCheckInTime(now.plusMinutes(90)); bookings.saveAndFlush(parked);
        var intent = request(1, 2); intent.setExpectedCheckInTime(now.plusMinutes(100));
        rejects(intent);
    }

    @Test void futureReservationAfterPlannedDepartureIsAllowedBeforeOvertime() {
        existing(BookingStatusEnum.PARKING, -1, 2);
        assertNotNull(service.createBooking(7L, request(3, 4)));
    }

    @Test void idempotentReplayReturnsTheOriginalBooking() {
        var intent = request(2, 3);
        var first = service.createBooking(7L, intent);
        assertEquals(first.getId(), service.createBooking(7L, intent).getId());
        verify(lots, times(1)).holdSlot(anyLong(), any());
    }

    @Test void concurrentCreatesForOneVehicleCommitOnlyOneReservation() throws Exception {
        var start = new CountDownLatch(1);
        var intents = new ArrayList<CreateBookingRequest>();
        for (int i = 0; i < 4; i++) intents.add(request(2, 3));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<Future<Object>>();
            for (var intent : intents) futures.add(pool.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                try { return service.createBooking(7L, intent); }
                catch (AppException conflict) { return conflict.getErrorCode(); }
            }));
            start.countDown();
            var results = new ArrayList<Object>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(BookingDto.class::isInstance).count());
            assertEquals(3, results.stream().filter(ErrorCode.CONFLICT::equals).count());
        }
        assertEquals(1, bookings.count());
        verify(lots, times(1)).holdSlot(anyLong(), any());
    }
}
