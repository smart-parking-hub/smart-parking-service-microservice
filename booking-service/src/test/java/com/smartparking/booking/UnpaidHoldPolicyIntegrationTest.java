package com.smartparking.booking;

import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.*;
import com.smartparking.booking.domain.HoldRestrictionException;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.*;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import com.smartparking.booking.infrastructure.persistence.HoldPolicyStore;
import com.smartparking.booking.infrastructure.scheduler.BookingExpirationScheduler;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.smartparking.booking.domain.HoldRestrictionException.Reason.*;
import static com.smartparking.booking.infrastructure.client.HoldPaymentAssessmentClient.Outcome.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:hold_policy_it;DB_CLOSE_DELAY=-1;MODE=MySQL;LOCK_TIMEOUT=15000",
        "spring.kafka.listener.auto-startup=false", "app.booking.hold-policy.rate-limit=10"})
class UnpaidHoldPolicyIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("HOLD_POLICY_MYSQL_TEST_URL");
        if (url==null || url.isBlank()) return;
        if (!url.matches("jdbc:mysql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/[a-z0-9_]+_hold_policy_it(?:\\?.*)?"))
            throw new IllegalArgumentException("Requires an isolated local *_hold_policy_it database");
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.driver-class-name",()->"com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username",()->"root");
        registry.add("spring.datasource.password",()->System.getenv("HOLD_POLICY_MYSQL_TEST_PASSWORD"));
        registry.add("spring.sql.init.mode",()->"always");
    }
    @Autowired BookingService service;
    @Autowired BookingRepository bookings;
    @Autowired JdbcTemplate jdbc;
    @Autowired HoldRateLimiter rate;
    @Autowired HoldPolicyStore store;
    @Autowired PlatformTransactionManager transactions;
    @Autowired BookingExpirationScheduler expiration;
    @MockBean OwnedVehicleClient vehicles;
    @MockBean ParkingLotClient lots;
    @MockBean OutboxPublisherService events;
    @MockBean QueueService queue;
    @MockBean HoldPaymentAssessmentClient payments;
    @MockBean KafkaTemplate<String,Object> kafka;
    private LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withNano(0); }
    @BeforeEach void setup() {
        bookings.deleteAll();
        jdbc.update("DELETE FROM booking_unpaid_hold_releases");
        jdbc.update("DELETE FROM booking_hold_rate_windows");
        jdbc.update("DELETE FROM booking_hold_policy_locks");
        when(vehicles.require(anyLong())).thenAnswer(c->new OwnedVehicleClient.Vehicle(c.getArgument(0),"51C-TEST",VehicleTypeEnum.LIGHT_TRUCK,"ACTIVE"));
        when(lots.holdSlot(anyLong(),any())).thenReturn(ApiResponse.ok(SlotHoldResponse.builder().success(true)
                .merchantId(99L).baseHourlyPrice(new BigDecimal("20000")).build()));
        when(payments.assess(anyLong())).thenReturn(UNPAID);
    }
    private CreateBookingRequest request(long vehicle, long lot) {
        return CreateBookingRequest.builder().requestId(UUID.randomUUID().toString()).vehicleId(vehicle).parkingLotId(lot)
                .vehicleType(VehicleTypeEnum.LIGHT_TRUCK).expectedCheckInTime(now().withMinute(0).withSecond(0).withNano(0).plusHours(1))
                .expectedCheckOutTime(now().withMinute(0).withSecond(0).withNano(0).plusHours(3)).build();
    }
    private List<Object> concurrent(IntFunction<Object> action) throws Exception {
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var tasks=new ArrayList<Future<Object>>();
            for(int i=0;i<8;i++) { final int index=i; tasks.add(pool.submit(()->{
                assertTrue(start.await(5,TimeUnit.SECONDS));
                try { return action.apply(index); } catch(HoldRestrictionException e) { return e; }
            })); }
            start.countDown();var result=new ArrayList<Object>();
            for(var task:tasks) result.add(task.get(30,TimeUnit.SECONDS));
            return result;
        }
    }
    @Test void sameVehicleConcurrentRequestsHoldOnlyOnce() throws Exception {
        var results=concurrent(i->service.createBooking(7L,request(11,5)));
        assertEquals(7,results.stream().filter(HoldRestrictionException.class::isInstance).count());
        assertEquals(1,bookings.count());verify(lots,times(1)).holdSlot(anyLong(),any());
    }
    @Test void sameAccountWithDifferentVehiclesCannotCrossQuotaConcurrently() throws Exception {
        var results=concurrent(i->service.createBooking(7L,request(11+i,5)));
        assertEquals(5,results.stream().filter(HoldRestrictionException.class::isInstance).count());
        assertEquals(3,bookings.count());verify(lots,times(3)).holdSlot(anyLong(),any());
    }
    @Test void unrelatedAccountsCanCreateAtTheSameTime() throws Exception {
        var results=concurrent(i->service.createBooking(70L+i,request(110+i,5)));
        assertEquals(0,results.stream().filter(HoldRestrictionException.class::isInstance).count());
        assertEquals(8,bookings.count());
    }
    @Test void simultaneousAndCompletedReplayDoNotCreateAnotherHoldOrExtendDeadline() throws Exception {
        var intent=request(11,5);
        var results=concurrent(i->service.createBooking(7L,intent));
        assertEquals(1,bookings.count());var row=bookings.findAll().getFirst();
        for(var result:results) assertEquals(row.getId(),((com.smartparking.booking.application.dto.BookingDto)result).getId());
        jdbc.update("UPDATE booking_hold_rate_windows SET request_count=10 WHERE driver_id=7");
        var replay=service.createBooking(7L,intent);
        assertEquals(row.getId(),replay.getId());assertEquals(row.getExpiresAt(),replay.getExpiresAt());
        verify(lots,times(1)).holdSlot(anyLong(),any());
    }
    private void cancelTwice() {
        for(long vehicle=11;vehicle<13;vehicle++) {
            var booking=service.createBooking(7L,request(vehicle,5));
            service.cancelBooking(7L,booking.getId(),null);
        }
    }
    @Test void twoUnpaidCancellationsBlockSameLotAcrossVehiclesWithoutExtendingCooldown() {
        cancelTwice();
        var releasedAt=now().minusMinutes(2);
        jdbc.update("UPDATE booking_unpaid_hold_releases SET released_at=?",releasedAt);
        var first=assertThrows(HoldRestrictionException.class,()->service.createBooking(7L,request(15,5)));
        var retry=assertThrows(HoldRestrictionException.class,()->service.createBooking(7L,request(16,5)));
        assertEquals(COOLDOWN,first.getDetails().reason());
        assertEquals(releasedAt.plusMinutes(30),first.getDetails().retryAt());
        assertEquals(first.getDetails().retryAt(),retry.getDetails().retryAt());
        service.createBooking(7L,request(15,6)); // Scoped to the abused lot.
        verify(lots,times(3)).holdSlot(anyLong(),any());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM booking_unpaid_hold_releases",Integer.class));
    }
    @Test void unknownEvidenceIsTemporaryAndPaidEvidenceRevokesTheRestriction() {
        cancelTwice();when(payments.assess(anyLong())).thenReturn(UNKNOWN);
        var unknown=assertThrows(HoldRestrictionException.class,()->service.createBooking(7L,request(15,5)));
        assertEquals(PAYMENT_CHECK_PENDING,unknown.getDetails().reason());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_unpaid_hold_releases WHERE assessment='UNPAID'",Integer.class));
        when(payments.assess(anyLong())).thenReturn(UNPAID);
        assertEquals(COOLDOWN,assertThrows(HoldRestrictionException.class,()->service.createBooking(7L,request(15,5))).getDetails().reason());
        when(payments.assess(anyLong())).thenReturn(PROTECTED);
        assertDoesNotThrow(()->service.createBooking(7L,request(15,5)));
    }
    @Test void expiryCreatesOneReleaseButPaidNoShowCreatesNone() {
        var expired=service.createBooking(7L,request(11,5));
        var paid=service.createBooking(7L,request(12,5));
        var a=bookings.findById(expired.getId()).orElseThrow();a.setExpiresAt(now().minusMinutes(5));bookings.saveAndFlush(a);
        var b=bookings.findById(paid.getId()).orElseThrow();b.setStatus(BookingStatusEnum.PENDING_CHECKIN);b.setExpiresAt(now().minusMinutes(5));bookings.saveAndFlush(b);
        expiration.expireUnpaidBookings();expiration.expireUnpaidBookings();expiration.expireNoShowBookings();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_unpaid_hold_releases",Integer.class));
        assertTrue(store.hasRelease(expired.getId()));assertFalse(store.hasRelease(paid.getId()));
        var template=new TransactionTemplate(transactions);
        template.executeWithoutResult(s->store.recordRelease(a,"EXPIRED",now().plusHours(1)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM booking_unpaid_hold_releases",Integer.class));
    }
    @Test void rateWindowSurvivesRequestRollbackAndResetsAtItsOriginalBoundary() {
        var time=now();var template=new TransactionTemplate(transactions);
        for(int i=0;i<10;i++) template.executeWithoutResult(status->{rate.acquire(7L,time);status.setRollbackOnly();});
        var blocked=assertThrows(HoldRestrictionException.class,()->rate.acquire(7L,time.plusSeconds(59)));
        assertEquals(RATE_LIMIT,blocked.getDetails().reason());
        assertEquals(time.plusSeconds(60),blocked.getDetails().retryAt(),"The persisted wall-clock window must not change with the JVM timezone");
        assertDoesNotThrow(()->rate.acquire(7L,time.plusSeconds(60)));
    }
    @Test void failedSlotCreationDoesNotRecordAnAbandonedHold() {
        when(lots.holdSlot(anyLong(),any())).thenThrow(new RuntimeException("lot unavailable"));
        assertThrows(RuntimeException.class,()->service.createBooking(7L,request(11,5)));
        assertEquals(0,bookings.count());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_unpaid_hold_releases",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM booking_hold_rate_windows WHERE driver_id=7",Integer.class));
    }
    @Test void futureRateWindowRecoversAndPersistsANewShortWindow() {
        var time=now();
        jdbc.update("INSERT INTO booking_hold_rate_windows (driver_id,window_started_at,request_count) VALUES (?,?,?)",
                7L,time.plusDays(1),10);
        for(int i=0;i<10;i++) rate.acquire(7L,time);
        assertEquals(time,jdbc.queryForObject("SELECT window_started_at FROM booking_hold_rate_windows WHERE driver_id=7",LocalDateTime.class));
        for(int seconds:new int[]{1,30,59}) {
            var blocked=assertThrows(HoldRestrictionException.class,()->rate.acquire(7L,time.plusSeconds(seconds)));
            assertEquals(time.plusSeconds(60),blocked.getDetails().retryAt());
        }
        assertEquals(10,jdbc.queryForObject("SELECT request_count FROM booking_hold_rate_windows WHERE driver_id=7",Integer.class));
        assertDoesNotThrow(()->rate.acquire(7L,time.plusSeconds(60)));
        assertEquals(1,jdbc.queryForObject("SELECT request_count FROM booking_hold_rate_windows WHERE driver_id=7",Integer.class));
    }
    @Test void concurrentRateRequestsCannotExceedTheThreshold() throws Exception {
        var time=now();
        for(int i=0;i<8;i++) rate.acquire(7L,time);
        var results=concurrent(i->{rate.acquire(7L,time);return Boolean.TRUE;});
        assertEquals(2,results.stream().filter(Boolean.TRUE::equals).count());
        assertEquals(6,results.stream().filter(HoldRestrictionException.class::isInstance).count());
        assertEquals(10,jdbc.queryForObject("SELECT request_count FROM booking_hold_rate_windows WHERE driver_id=7",Integer.class));
    }
    @Test void bookingCreationIgnoresAnExhaustedFutureRateWindowAndAllowsImmediateRetry() {
        var future=now().plusDays(1);
        jdbc.update("INSERT INTO booking_hold_rate_windows (driver_id,window_started_at,request_count) VALUES (?,?,?)",7L,future,1000);
        when(lots.holdSlot(anyLong(),any())).thenReturn(ApiResponse.ok(SlotHoldResponse.builder().success(false).message("Full").build()));
        for(int i=0;i<12;i++) {
            var failure=assertThrows(com.smartparking.common.exception.AppException.class,()->service.createBooking(7L,request(11,5)));
            assertEquals(com.smartparking.common.exception.ErrorCode.SLOT_NOT_AVAILABLE,failure.getErrorCode());
        }
        when(lots.holdSlot(anyLong(),any())).thenReturn(ApiResponse.ok(SlotHoldResponse.builder().success(true)
                .merchantId(99L).baseHourlyPrice(new BigDecimal("20000")).build()));
        var intent=request(11,5);
        var created=service.createBooking(7L,intent);
        assertEquals(created.getId(),service.createBooking(7L,intent).getId());
        assertEquals(1,bookings.count());
        assertEquals(1000,jdbc.queryForObject("SELECT request_count FROM booking_hold_rate_windows WHERE driver_id=7",Integer.class));
        assertEquals(future,jdbc.queryForObject("SELECT window_started_at FROM booking_hold_rate_windows WHERE driver_id=7",LocalDateTime.class));
    }
}
