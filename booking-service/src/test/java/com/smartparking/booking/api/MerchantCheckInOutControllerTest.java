package com.smartparking.booking.api;

import com.smartparking.booking.api.controller.MerchantCheckInOutController;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CheckInRequest;
import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.dto.ConfirmCashRequest;
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

import java.math.BigDecimal;
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
        when(bookingService.getMerchantBookings(eq(200L), any(), any())).thenReturn(page);

        ResponseEntity<ApiResponse<PageResponse<BookingDto>>> response =
                controller.getMerchantBookings(user, null, PageRequest.of(0, 20));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getData().getContent().size());
    }

    @Test
    @DisplayName("getMerchantBookingDetail delegates to bookingService and returns booking details")
    void testGetMerchantBookingDetail() {
        // Arrange
        Long bookingId = 99L;
        BookingDto dto = BookingDto.builder().id(bookingId).bookingCode("BK-99").build();
        when(bookingService.getMerchantBookingDetail(200L, bookingId)).thenReturn(dto);

        // Act
        ResponseEntity<ApiResponse<BookingDto>> response = controller.getMerchantBookingDetail(user, bookingId);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("BK-99", response.getBody().getData().getBookingCode());
        verify(bookingService, times(1)).getMerchantBookingDetail(200L, bookingId);
    }

    @Test
    @DisplayName("confirmCashPayment with amount delegates to checkInOutService and returns 200 OK")
    void testConfirmCashPayment_WithAmount() {
        // Arrange
        ConfirmCashRequest req = ConfirmCashRequest.builder()
                .bookingCode("BK-12345")
                .parkingLotId(10L)
                .amount(new BigDecimal("60000"))
                .build();

        // Act
        ResponseEntity<ApiResponse<Void>> response = controller.confirmCashPayment(user, req);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Xác nhận thu tiền mặt và trả chỗ thành công!", response.getBody().getMessage());
        assertNull(response.getBody().getData());
        verify(checkInOutService, times(1)).confirmCashPayment(200L, "BK-12345", 10L, new BigDecimal("60000"));
    }

    @Test
    @DisplayName("confirmCashPayment without amount delegates to checkInOutService with null amount and returns 200 OK")
    void testConfirmCashPayment_WithoutAmount() {
        // Arrange
        ConfirmCashRequest req = ConfirmCashRequest.builder()
                .bookingCode("BK-12345")
                .parkingLotId(10L)
                .build();

        // Act
        ResponseEntity<ApiResponse<Void>> response = controller.confirmCashPayment(user, req);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Xác nhận thu tiền mặt và trả chỗ thành công!", response.getBody().getMessage());
        assertNull(response.getBody().getData());
        verify(checkInOutService, times(1)).confirmCashPayment(200L, "BK-12345", 10L, null);
    }
}
