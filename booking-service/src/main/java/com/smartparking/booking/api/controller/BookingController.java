package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.application.dto.CancelBookingResponse;
import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<BookingDto>> createBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateBookingRequest request) {
        BookingDto dto = bookingService.createBooking(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(dto, "Đặt chỗ thành công"));
    }

    @GetMapping
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<PageResponse<BookingDto>>> getMyBookings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<BookingDto> page = bookingService.getMyBookings(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingDto>> getBookingDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        BookingDto dto = bookingService.getBookingDetail(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('DRIVER')")
    public ResponseEntity<ApiResponse<CancelBookingResponse>> cancelBooking(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request) {
        if (request == null) {
            request = new CancelBookingRequest();
        }
        CancelBookingResponse result = bookingService.cancelBooking(user.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(result, result.getMessage()));
    }
}
