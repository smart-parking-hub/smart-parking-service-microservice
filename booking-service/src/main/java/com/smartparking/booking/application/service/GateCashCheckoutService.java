package com.smartparking.booking.application.service;

import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.CashCheckoutClient;
import com.smartparking.common.dto.CashCheckoutCommand;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.Objects;

/** No transaction spans the callback into booking: payment reserves the channel before calling back. */
@Service
@RequiredArgsConstructor
public class GateCashCheckoutService {
    private final BookingRepository bookings;
    private final CashCheckoutClient payments;

    public void confirm(Long merchantId, String code, Long lotId, BigDecimal amount) {
        var booking = bookings.findByBookingCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));
        if (merchantId == null || !Objects.equals(booking.getMerchantId(), merchantId)
                || lotId == null || !Objects.equals(lotId, booking.getParkingLotId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Không có quyền xác nhận thu tiền cho lượt đỗ này");
        if (booking.getTotalAmount() == null || booking.getAdvanceDeposit() == null || amount == null
                || amount.signum() <= 0 || amount.compareTo(booking.getTotalAmount().subtract(booking.getAdvanceDeposit())) != 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền mặt không khớp số tiền còn phải thu");
        if (booking.getStatus() == BookingStatusEnum.COMPLETED && "CASH".equals(booking.getFinalPaymentMethod())) return;
        if (booking.getStatus() != BookingStatusEnum.AWAITING_FINAL_PAYMENT || booking.getFinalPaymentMethod() != null)
            throw new AppException(ErrorCode.CONFLICT, "Lượt đỗ không còn chờ xác nhận tiền mặt");
        payments.confirm(booking.getId(), new CashCheckoutCommand(merchantId, lotId, amount));
    }
}
