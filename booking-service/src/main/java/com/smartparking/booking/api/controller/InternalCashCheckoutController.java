package com.smartparking.booking.api.controller;

import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequiredArgsConstructor
public class InternalCashCheckoutController {
    private final CheckInOutService checkout;
    private final BookingRepository bookings;
    @Value("${app.internal-service-token:}") private String serviceToken;

    @PostMapping("/api/v1/internal/bookings/{id}/confirm-cash-checkout")
    public ApiResponse<Boolean> confirm(@PathVariable Long id,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token,
            @RequestBody CashCheckoutCommand command) {
        if (serviceToken.isBlank() || token == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xác nhận nội bộ");
        var booking = bookings.findById(id).orElse(null);
        if (booking == null) return ApiResponse.ok(false);
        try {
            checkout.confirmCashPayment(command.merchantId(), booking.getBookingCode(), command.parkingLotId(), command.amount());
            return ApiResponse.ok(true);
        } catch (AppException failure) {
            if (failure.getErrorCode() == ErrorCode.BAD_REQUEST || failure.getErrorCode() == ErrorCode.FORBIDDEN
                    || failure.getErrorCode() == ErrorCode.CONFLICT || failure.getErrorCode() == ErrorCode.BOOKING_STATUS_INVALID)
                return ApiResponse.ok(false);
            throw failure; // Slot/RPC/commit failures are ambiguous and must retain the payment reservation.
        }
    }
}
