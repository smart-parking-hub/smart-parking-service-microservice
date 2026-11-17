package com.smartparking.booking.api.controller;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/internal/bookings")
public class InternalPaymentConfirmationController {
    private final BookingRepository repository;
    @Value("${app.internal-service-token:}") private String serviceToken;
    public record Confirmation(Long driverId, Long merchantId, BigDecimal amount, String paymentType, Long paymentId) {
        public Confirmation(Long driverId, Long merchantId, BigDecimal amount, String paymentType) {
            this(driverId, merchantId, amount, paymentType, null);
        }
    }
    @PostMapping("/{id}/confirm-payment") @Transactional
    public ApiResponse<Boolean> confirm(@PathVariable Long id,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token, @RequestBody Confirmation request) {
        authenticate(token);
        var booking = repository.findByIdForUpdate(id).orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại"));
        if (!booking.getDriverId().equals(request.driverId()) || booking.getMerchantId() == null || !booking.getMerchantId().equals(request.merchantId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Thanh toán không khớp người đặt hoặc chủ bãi");
        if ("DEPOSIT".equals(request.paymentType())) {
            if (request.amount() == null || request.amount().compareTo(booking.getAdvanceDeposit()) != 0)
                throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền cọc không khớp");
            if (booking.getStatus() == BookingStatusEnum.PENDING_CHECKIN || booking.getStatus() == BookingStatusEnum.PARKING || booking.getStatus() == BookingStatusEnum.COMPLETED) return ApiResponse.ok(true);
            if (booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT || booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(LocalDateTime.now(BUSINESS_ZONE))) return ApiResponse.ok(false);
            booking.setStatus(BookingStatusEnum.PENDING_CHECKIN);
            booking.setExpiresAt(booking.getExpectedCheckInTime().plusMinutes(60));
            repository.save(booking);
            return ApiResponse.ok(true);
        }
        // Final settlement remains event-driven; reserve no new slot and never revive a cancelled booking.
        if ("FINAL_PAYMENT".equals(request.paymentType()) && (booking.getStatus() == BookingStatusEnum.AWAITING_FINAL_PAYMENT || booking.getStatus() == BookingStatusEnum.COMPLETED)) {
            BigDecimal due = booking.getTotalAmount().subtract(booking.getAdvanceDeposit()).max(BigDecimal.ZERO);
            if (request.paymentId() == null || request.paymentId() <= 0 || due.signum() <= 0
                    || request.amount() == null || request.amount().compareTo(due) != 0) return ApiResponse.ok(false);
            String reference = String.valueOf(request.paymentId());
            if (booking.getFinalPaymentMethod() != null) return ApiResponse.ok(
                    "PAYOS".equals(booking.getFinalPaymentMethod()) && reference.equals(booking.getFinalPaymentReference()));
            if (booking.getStatus() != BookingStatusEnum.AWAITING_FINAL_PAYMENT) return ApiResponse.ok(false);
            booking.setFinalPaymentMethod("PAYOS");
            booking.setFinalPaymentReference(reference);
            booking.setFinalPaymentAmount(request.amount());
            repository.save(booking);
            return ApiResponse.ok(true);
        }
        return ApiResponse.ok(false);
    }
    private void authenticate(String token) {
        if (serviceToken == null || serviceToken.isBlank() || token == null || !MessageDigest.isEqual(serviceToken.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)))
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xác nhận thanh toán nội bộ");
    }
    @GetMapping("/{id}/hold-status")
    public ApiResponse<String> holdStatus(@PathVariable Long id,
            @RequestHeader(value = "X-Internal-Service-Token", required = false) String token) {
        authenticate(token);
        return ApiResponse.ok(repository.findById(id).map(booking -> booking.getStatus().name()).orElse("ABSENT"));
    }
}
