package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CheckInRequest;
import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.dto.ConfirmCashRequest;
import com.smartparking.booking.application.dto.MerchantBookingSummaryResponse;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.infrastructure.security.AuthenticatedUser;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/merchant")
@RequiredArgsConstructor
public class MerchantCheckInOutController {

    private final CheckInOutService checkInOutService;
    private final BookingService bookingService;
    private final com.smartparking.booking.application.service.GateCashCheckoutService cashCheckout;

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<Void>> checkIn(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CheckInRequest request) {
        checkInOutService.checkIn(user.getId(), request.getBookingCode(), request.getParkingLotId());
        return ResponseEntity.ok(ApiResponse.success(null, "Check-in thành công!"));
    }

    @PostMapping("/check-out")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<CheckOutResponse>> checkOut(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CheckInRequest request) {
        CheckOutResponse response = checkInOutService.checkOut(user.getId(), request.getBookingCode(), request.getParkingLotId());
        return ResponseEntity.ok(ApiResponse.success(response, "Check-out thành công!"));
    }

    @PostMapping("/confirm-cash")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<Void>> confirmCashPayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ConfirmCashRequest request) {
        cashCheckout.confirm(user.getId(), request.getBookingCode(), request.getParkingLotId(), request.getAmount());
        return ResponseEntity.ok(ApiResponse.success(null, "Xác nhận thu tiền mặt và trả chỗ thành công!"));
    }

    @GetMapping("/bookings")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<PageResponse<BookingDto>>> getMerchantBookings(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long parkingLotId,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<BookingDto> page = bookingService.getMerchantBookings(user.getId(), parkingLotId, pageable);
        return ResponseEntity.ok(ApiResponse.success(page, "Lấy danh sách đặt chỗ thành công"));
    }

    @GetMapping("/bookings/summary")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<MerchantBookingSummaryResponse>> getMerchantBookingSummary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) Long parkingLotId) {
        MerchantBookingSummaryResponse summary = bookingService.getMerchantBookingSummary(user.getId(), parkingLotId);
        return ResponseEntity.ok(ApiResponse.success(summary, "Lấy tổng hợp trạng thái đặt chỗ thành công"));
    }

    @GetMapping("/bookings/{id}")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<ApiResponse<BookingDto>> getMerchantBookingDetail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        BookingDto dto = bookingService.getMerchantBookingDetail(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(dto, "Lấy chi tiết đơn đặt chỗ thành công"));
    }
}
