package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.MerchantCheckInOutController;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CheckInRequest;
import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantCheckInOutControllerTest {

    @Mock
    private CheckInOutService checkInOutService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private MerchantCheckInOutController controller;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(200L, "merchant01", "MERCHANT");
    }

    @Test
    @DisplayName("checkIn delegates to checkInOutService")
    void testCheckIn() {
        CheckInRequest req = new CheckInRequest();
        req.setBookingCode("BK-12345");
        req.setParkingLotId(10L);

        ResponseEntity<ApiResponse<Void>> response = controller.checkIn(user, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(checkInOutService, times(1)).checkIn(200L, "BK-12345", 10L);
    }

    @Test
    @DisplayName("checkOut delegates to checkInOutService and returns CheckOutResponse")
    void testCheckOut() {
        CheckInRequest req = new CheckInRequest();
        req.setBookingCode("BK-12345");
        req.setParkingLotId(10L);

        CheckOutResponse outRes = CheckOutResponse.builder().bookingCode("BK-12345").build();
        when(checkInOutService.checkOut(200L, "BK-12345", 10L)).thenReturn(outRes);

        ResponseEntity<ApiResponse<CheckOutResponse>> response = controller.checkOut(user, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("BK-12345", response.getBody().getData().getBookingCode());
    }

    @Test
    @DisplayName("getMerchantBookings returns paginated bookings")
    void testGetMerchantBookings() {
        PageResponse<BookingDto> page = new PageResponse<>(new PageImpl<>(List.of(BookingDto.builder().id(1L).build())));
        when(bookingService.getMerchantBookings(eq(200L), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<BookingDto>>> response =
                controller.getMerchantBookings(user, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }
}
