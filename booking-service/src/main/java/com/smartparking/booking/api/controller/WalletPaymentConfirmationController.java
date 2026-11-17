package com.smartparking.booking.api.controller;

import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Objects;
import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

/** Receipts retain the original decision even if the driver cancels before payment capture recovers. */
@RestController
@RequiredArgsConstructor
public class WalletPaymentConfirmationController {
    private final BookingRepository bookings;
    private final JdbcTemplate jdbc;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public record Confirmation(Long paymentId, Long driverId, Long merchantId, BigDecimal amount) {}

    @PostMapping("/api/v1/internal/bookings/{id}/confirm-wallet-payment")
    @Transactional
    public ApiResponse<Boolean> confirm(@PathVariable Long id,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token,
            @RequestBody Confirmation request) {
        if (serviceToken == null || serviceToken.isBlank() || token == null || !MessageDigest.isEqual(
                serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xác nhận nội bộ");
        var booking = bookings.findByIdForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));
        if (request.paymentId() == null || request.paymentId() <= 0 || !Objects.equals(booking.getDriverId(), request.driverId())
                || !Objects.equals(booking.getMerchantId(), request.merchantId()) || request.amount() == null
                || request.amount().compareTo(booking.getAdvanceDeposit()) != 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Giao dịch ví không khớp booking");
        var receipt = jdbc.query("SELECT booking_id, accepted FROM wallet_payment_receipts WHERE payment_id=?",
                (r, n) -> new Object[]{r.getLong(1), r.getBoolean(2)}, request.paymentId());
        if (!receipt.isEmpty()) {
            if (!Objects.equals(id, receipt.getFirst()[0])) throw new AppException(ErrorCode.CONFLICT, "Mã thanh toán đã dùng cho booking khác");
            return ApiResponse.ok((Boolean) receipt.getFirst()[1]);
        }
        boolean accepted = booking.getStatus() == BookingStatusEnum.PENDING_PAYMENT && booking.getExpiresAt() != null
                && booking.getExpiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE));
        if (accepted) {
            booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
            booking.setExpiresAt(booking.getExpectedCheckInTime().plusMinutes(60));
            bookings.save(booking);
        }
        jdbc.update("INSERT INTO wallet_payment_receipts(payment_id,booking_id,accepted,created_at) VALUES(?,?,?,?)",
                request.paymentId(), id, accepted, LocalDateTime.now(BUSINESS_ZONE));
        return ApiResponse.ok(accepted);
    }
}
