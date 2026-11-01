package com.smartparking.booking;

import com.smartparking.booking.application.dto.*;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.application.service.QueueService;
import com.smartparking.booking.application.service.ReviewService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.domain.repository.OutboxEventRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@SpringBootTest
@Transactional
public class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private CheckInOutService checkInOutService;

    @Autowired
    private QueueService queueService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockBean
    private ParkingLotClient parkingLotClient;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final Long driverUserId = 101L;
    private final Long merchantUserId = 202L;
    private final Long parkingLotId = 303L;
    private final Long vehicleId = 404L;

    @BeforeEach
    void setupMocks() {
        SlotHoldResponse successHold = SlotHoldResponse.builder()
                .slotId(1L)
                .parkingLotId(parkingLotId)
                .success(true)
                .message("Slot held")
                .build();
        Mockito.when(parkingLotClient.holdSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(successHold));

        Mockito.when(parkingLotClient.releaseSlot(eq(parkingLotId), any(SlotHoldRequest.class)))
                .thenReturn(ApiResponse.success(null));
    }

    @Test
    @DisplayName("Create booking successfully with slot hold and outbox event")
    void testCreateBooking() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(vehicleId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusHours(1))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(3))
                .build();

        BookingDto dto = bookingService.createBooking(driverUserId, req);

        assertNotNull(dto);
        assertNotNull(dto.getId());
        assertNotNull(dto.getBookingCode());
        assertEquals(BookingStatusEnum.PENDING_PAYMENT, dto.getStatus());
        assertEquals(new BigDecimal("8000"), dto.getAdvanceDeposit());

        assertTrue(bookingRepository.findById(dto.getId()).isPresent());
        assertFalse(outboxEventRepository.findAll().isEmpty());
    }

    @Test
    @DisplayName("Cancel booking releases slot and marks booking cancelled")
    void testCancelBooking() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(vehicleId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusHours(1))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(3))
                .build();

        BookingDto created = bookingService.createBooking(driverUserId, req);

        CancelBookingRequest cancelReq = CancelBookingRequest.builder()
                .reason("Thay đổi lịch trình vận tải")
                .build();

        CancelBookingResponse cancelRes = bookingService.cancelBooking(driverUserId, created.getId(), cancelReq);

        assertNotNull(cancelRes);
        assertEquals("CANCELLED", cancelRes.getStatus());

        Booking booking = bookingRepository.findById(created.getId()).orElseThrow();
        assertEquals(BookingStatusEnum.CANCELLED, booking.getStatus());
    }

    @Test
    @DisplayName("Check-in and check-out full lifecycle")
    void testCheckInOutLifecycle() {
        CreateBookingRequest req = CreateBookingRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(vehicleId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .expectedCheckInTime(LocalDateTime.now().plusMinutes(10))
                .expectedCheckOutTime(LocalDateTime.now().plusHours(2))
                .build();

        BookingDto created = bookingService.createBooking(driverUserId, req);

        // Simulate deposit payment success -> PENDING_CHECKIN
        Booking booking = bookingRepository.findById(created.getId()).orElseThrow();
        booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
        booking.setMerchantId(merchantUserId);
        bookingRepository.save(booking);

        // Merchant Check-in
        checkInOutService.checkIn(merchantUserId, booking.getBookingCode(), parkingLotId);

        Booking checkedIn = bookingRepository.findById(created.getId()).orElseThrow();
        assertEquals(BookingStatusEnum.PARKING, checkedIn.getStatus());
        assertNotNull(checkedIn.getCheckInTime());

        // Merchant Check-out
        CheckOutResponse checkOutRes = checkInOutService.checkOut(merchantUserId, booking.getBookingCode(), parkingLotId);
        assertNotNull(checkOutRes);
        assertEquals(booking.getBookingCode(), checkOutRes.getBookingCode());
        assertNotNull(checkOutRes.getTotalAmount());
    }

    @Test
    @DisplayName("Join queue and cancel queue")
    void testQueueOperations() {
        JoinQueueRequest req = JoinQueueRequest.builder()
                .parkingLotId(parkingLotId)
                .vehicleId(vehicleId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .build();

        QueueDto queue = queueService.joinQueue(driverUserId, req);
        assertNotNull(queue);
        assertEquals(1, queue.getPosition());

        QueueDto myQueue = queueService.getMyQueue(driverUserId);
        assertNotNull(myQueue);
        assertEquals(queue.getId(), myQueue.getId());

        queueService.cancelQueue(driverUserId, queue.getId());
        assertNull(queueService.getMyQueue(driverUserId));
    }

    @Test
    @DisplayName("Create review for completed booking")
    void testCreateReview() {
        Booking booking = Booking.builder()
                .bookingCode("BK_COMPLETED_TEST")
                .driverId(driverUserId)
                .merchantId(merchantUserId)
                .parkingLotId(parkingLotId)
                .vehicleId(vehicleId)
                .vehicleType(VehicleTypeEnum.CONTAINER)
                .status(BookingStatusEnum.COMPLETED)
                .advanceDeposit(BigDecimal.valueOf(50000))
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        booking = bookingRepository.save(booking);

        CreateReviewRequest req = CreateReviewRequest.builder()
                .bookingId(booking.getId())
                .rating(5)
                .comment("Bãi xe rộng rãi, nhân viên hỗ trợ nhiệt tình.")
                .build();

        ReviewDto review = reviewService.createReview(driverUserId, req);
        assertNotNull(review);
        assertEquals(5, review.getRating());
        assertEquals("Bãi xe rộng rãi, nhân viên hỗ trợ nhiệt tình.", review.getComment());
    }
}
