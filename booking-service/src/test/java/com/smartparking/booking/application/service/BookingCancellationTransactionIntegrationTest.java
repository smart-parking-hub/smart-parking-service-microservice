package com.smartparking.booking.application.service;

import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.domain.BookingRefundPolicy;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.OwnedVehicleClient;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import com.smartparking.common.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Real Spring transaction proxy + real JPA repositories on an isolated H2 (MODE=MySQL) database.
 * The test class is intentionally NOT @Transactional, so commit/rollback and afterCommit callbacks
 * happen exactly as in production; state is verified afterwards through a separate JDBC connection.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:booking_cancel_tx_it;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
class BookingCancellationTransactionIntegrationTest {

    private static final long DRIVER_ID = 9101L;
    private static final long PARKING_LOT_ID = 9303L;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private ParkingLotClient parkingLotClient;

    @MockBean
    private OwnedVehicleClient ownedVehicleClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private Long bookingId;

    @BeforeEach
    void setupAndSeedCommittedBooking() throws Exception {
        dropFailureConstraint();
        cleanupTestData();
        Booking booking = Booking.builder()
                .bookingCode("BK-TX-" + UUID.randomUUID().toString().substring(0, 8))
                .driverId(DRIVER_ID)
                .vehicleId(9404L)
                .parkingLotId(PARKING_LOT_ID)
                .merchantId(9202L)
                .vehicleType(VehicleTypeEnum.SEDAN_SUV)
                .status(BookingStatusEnum.PENDING_CHECKIN)
                .advanceDeposit(new BigDecimal("50000.00"))
                .expectedCheckInTime(LocalDateTime.now(BookingRefundPolicy.BUSINESS_ZONE).plusHours(5))
                .expectedCheckOutTime(LocalDateTime.now(BookingRefundPolicy.BUSINESS_ZONE).plusHours(7))
                .expiresAt(LocalDateTime.now(BookingRefundPolicy.BUSINESS_ZONE).plusHours(6))
                .build();
        bookingId = bookingRepository.save(booking).getId(); // committed by repository's own transaction
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "Test must not run inside an outer transaction");
    }

    @AfterEach
    void dropFailureConstraintAndCleanup() throws Exception {
        dropFailureConstraint();
        cleanupTestData();
    }

    private void dropFailureConstraint() throws Exception {
        execute("ALTER TABLE outbox_events DROP CONSTRAINT IF EXISTS ck_it_reject_cancel_outbox");
    }

    private void cleanupTestData() throws Exception {
        execute("DELETE FROM outbox_events");
        execute("DELETE FROM bookings WHERE driver_id = " + DRIVER_ID);
    }

    @Test
    @DisplayName("a) Outbox INSERT fails at flush: releaseSlot never called, nothing persisted, no Kafka")
    void outboxFlushFailureRollsBackBeforeReleaseSlot() throws Exception {
        // Real DB-level failure that only surfaces when the outbox INSERT is actually executed (flushed).
        execute("ALTER TABLE outbox_events ADD CONSTRAINT ck_it_reject_cancel_outbox CHECK (event_type <> 'BOOKING_CANCELLED')");

        assertThrows(RuntimeException.class,
                () -> bookingService.cancelBooking(DRIVER_ID, bookingId, CancelBookingRequest.builder().reason("change plan").build()));

        verify(parkingLotClient, never()).releaseSlot(anyLong(), any(SlotHoldRequest.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertEquals("PENDING_CHECKIN", bookingStatusViaNewConnection());
        assertEquals(0, cancelledOutboxCountViaNewConnection());
    }

    @Test
    @DisplayName("b) releaseSlot throws: booking and outbox rolled back, no Kafka")
    void releaseSlotFailureRollsBackBookingAndOutbox() throws Exception {
        when(parkingLotClient.releaseSlot(eq(PARKING_LOT_ID), any(SlotHoldRequest.class)))
                .thenThrow(new RuntimeException("parking-lot-service unavailable"));

        assertThrows(AppException.class,
                () -> bookingService.cancelBooking(DRIVER_ID, bookingId, CancelBookingRequest.builder().reason("change plan").build()));

        verify(parkingLotClient, times(1)).releaseSlot(eq(PARKING_LOT_ID), any(SlotHoldRequest.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        assertEquals("PENDING_CHECKIN", bookingStatusViaNewConnection());
        assertEquals(0, cancelledOutboxCountViaNewConnection());
    }

    @Test
    @DisplayName("c) Success: booking CANCELLED and outbox committed; Kafka sent only after commit")
    void successCommitsThenPublishesToKafka() throws Exception {
        when(parkingLotClient.releaseSlot(eq(PARKING_LOT_ID), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(null));
        AtomicReference<String> statusSeenByKafka = new AtomicReference<>();
        AtomicReference<Integer> outboxSeenByKafka = new AtomicReference<>();
        when(kafkaTemplate.send(eq("booking.events"), eq(String.valueOf(bookingId)), any()))
                .thenAnswer(inv -> {
                    // Separate pool connection: only committed data is visible here.
                    statusSeenByKafka.set(bookingStatusViaNewConnection());
                    outboxSeenByKafka.set(cancelledOutboxCountViaNewConnection());
                    return new CompletableFuture<>();
                });

        bookingService.cancelBooking(DRIVER_ID, bookingId, CancelBookingRequest.builder().reason("change plan").build());

        verify(kafkaTemplate, times(1)).send(eq("booking.events"), eq(String.valueOf(bookingId)), any());
        assertEquals("CANCELLED", statusSeenByKafka.get(), "Kafka must be invoked only after booking commit");
        assertEquals(1, outboxSeenByKafka.get(), "Kafka must be invoked only after outbox commit");
        assertEquals("CANCELLED", bookingStatusViaNewConnection());
        assertEquals(1, cancelledOutboxCountViaNewConnection());
    }

    private void execute(String sql) throws Exception {
        try (Connection c = dataSource.getConnection(); var st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private String bookingStatusViaNewConnection() throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT status FROM bookings WHERE id = ?")) {
            ps.setLong(1, bookingId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString(1);
            }
        }
    }

    private int cancelledOutboxCountViaNewConnection() throws Exception {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'BOOKING_CANCELLED'")) {
            ps.setString(1, String.valueOf(bookingId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
