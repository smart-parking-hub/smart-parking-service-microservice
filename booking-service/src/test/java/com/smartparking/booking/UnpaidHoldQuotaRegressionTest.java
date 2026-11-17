package com.smartparking.booking;

import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.OwnedVehicleClient;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.VehicleTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:hold_quota_regression;DB_CLOSE_DELAY=-1;MODE=MySQL",
        "spring.kafka.listener.auto-startup=false", "app.booking.hold-policy.rate-limit=1000"})
class UnpaidHoldQuotaRegressionTest {
    @Autowired BookingService service;
    @Autowired BookingRepository bookings;
    @MockBean OwnedVehicleClient vehicles;
    @MockBean ParkingLotClient lots;
    @MockBean OutboxPublisherService events;
    @MockBean KafkaTemplate<String,Object> kafka;
    @BeforeEach void setup() {
        bookings.deleteAll();
        when(vehicles.require(anyLong())).thenAnswer(call -> new OwnedVehicleClient.Vehicle(call.getArgument(0), "51C-TEST", VehicleTypeEnum.LIGHT_TRUCK, "ACTIVE"));
        when(lots.holdSlot(anyLong(), any())).thenReturn(ApiResponse.ok(SlotHoldResponse.builder()
                .success(true).merchantId(99L).baseHourlyPrice(new BigDecimal("20000")).build()));
    }
    private CreateBookingRequest request(long vehicle) {
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).withMinute(0).withSecond(0).withNano(0);
        return CreateBookingRequest.builder().requestId(UUID.randomUUID().toString()).vehicleId(vehicle)
                .parkingLotId(5L).vehicleType(VehicleTypeEnum.LIGHT_TRUCK).expectedCheckInTime(now.plusHours(1))
                .expectedCheckOutTime(now.plusHours(3)).build();
    }
    @Test void differentIntentCannotReserveASecondUnpaidPlaceForTheSameVehicle() {
        service.createBooking(7L, request(11L));
        assertThrows(RuntimeException.class, () -> service.createBooking(7L, request(11L)));
        verify(lots,times(1)).holdSlot(anyLong(),any());
        assertEquals(1,bookings.count());
    }
    @Test void accountCannotReserveAFourthUnpaidPlaceUsingAnotherVehicle() {
        for (long id=11;id<=13;id++) service.createBooking(7L,request(id));
        assertThrows(RuntimeException.class, () -> service.createBooking(7L,request(14L)));
        verify(lots,times(3)).holdSlot(anyLong(),any());
        assertEquals(3,bookings.count());
    }
}
