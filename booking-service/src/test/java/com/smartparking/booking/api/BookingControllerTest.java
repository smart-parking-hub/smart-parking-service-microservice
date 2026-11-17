package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.BookingController;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.application.dto.CancelBookingResponse;
import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.BookingService;
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
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        user = new AuthenticatedUser(100L, "driver01", "DRIVER");
    }

    @Test
    @DisplayName("createBooking returns CREATED status")
    void testCreateBooking() {
        CreateBookingRequest req = new CreateBookingRequest();
        BookingDto dto = BookingDto.builder().id(1L).build();
        when(bookingService.createBooking(100L, req)).thenReturn(dto);

        ResponseEntity<ApiResponse<BookingDto>> response = bookingController.createBooking(user, req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("getMyBookings returns paginated bookings")
    void testGetMyBookings() {
        PageResponse<BookingDto> page = new PageResponse<>(new PageImpl<>(List.of(BookingDto.builder().id(1L).build())));
        when(bookingService.getMyBookings(eq(100L), any(), any(), any(), any(), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<BookingDto>>> response =
                bookingController.getMyBookings(user, null, null, null, null, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getMyBookings with filters returns filtered bookings")
    void testGetMyBookingsWithFilters() {
        PageResponse<BookingDto> page = new PageResponse<>(new PageImpl<>(List.of(BookingDto.builder().id(1L).bookingCode("BK-01").build())));
        when(bookingService.getMyBookings(eq(100L), eq("BK-01"), any(), any(), any(), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<BookingDto>>> response =
                bookingController.getMyBookings(user, "BK-01", null, null, null, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getBookingDetail returns booking details")
    void testGetBookingDetail() {
        BookingDto dto = BookingDto.builder().id(1L).build();
        when(bookingService.getBookingDetail(100L, 1L)).thenReturn(dto);

        ResponseEntity<ApiResponse<BookingDto>> response = bookingController.getBookingDetail(user, 1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1L, response.getBody().getData().getId());
    }

    @Test
    @DisplayName("cancelBooking handles both null and non-null request")
    void testCancelBooking() {
        CancelBookingResponse cancelRes = CancelBookingResponse.builder().message("Cancelled").build();
        when(bookingService.cancelBooking(eq(100L), eq(1L), any())).thenReturn(cancelRes);

        // with request null
        ResponseEntity<ApiResponse<CancelBookingResponse>> res1 =
                bookingController.cancelBooking(user, 1L, null);
        assertEquals(HttpStatus.OK, res1.getStatusCode());

        // with request non-null
        ResponseEntity<ApiResponse<CancelBookingResponse>> res2 =
                bookingController.cancelBooking(user, 1L, new CancelBookingRequest());
        assertEquals(HttpStatus.OK, res2.getStatusCode());
    }
}
