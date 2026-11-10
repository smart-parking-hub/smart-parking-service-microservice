package com.smartparking.booking.domain;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Domain policy for booking cancellation and deposit refund calculation (D4).
 * Uses Asia/Ho_Chi_Minh business time and HALF_UP integer VND rounding.
 */
public final class BookingRefundPolicy {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REQUIRES_RECONCILIATION = "REQUIRES_RECONCILIATION";

    public static final String POLICY_FULL_100 = "FULL_100_AT_OR_BEFORE_2H";
    public static final String POLICY_PARTIAL_50 = "PARTIAL_50_WITHIN_2H";
    public static final String POLICY_ZERO_AT_OR_AFTER_CHECKIN = "NO_REFUND_AT_OR_AFTER_CHECKIN";
    public static final String POLICY_ZERO_UNPAID = "NO_REFUND_UNPAID";
    public static final String POLICY_ZERO_NO_SHOW = "NO_REFUND_NO_SHOW";

    private BookingRefundPolicy() {
    }

    public record RefundDecision(
            boolean refundRequested,
            BigDecimal refundAmount,
            int refundPercentage,
            String refundPolicy,
            String refundStatus,
            LocalDateTime cancelledAt
    ) {
    }

    public static RefundDecision evaluateDriverCancel(
            BookingStatusEnum status,
            BigDecimal advanceDeposit,
            LocalDateTime expectedCheckInTime,
            LocalDateTime cancelTime) {

        if (status != BookingStatusEnum.PENDING_PAYMENT && status != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Chỉ có thể hủy booking chưa check-in");
        }

        LocalDateTime effectiveCancelTime = cancelTime != null ? cancelTime : LocalDateTime.now(BUSINESS_ZONE);

        if (status == BookingStatusEnum.PENDING_PAYMENT) {
            return new RefundDecision(
                    false,
                    BigDecimal.ZERO,
                    0,
                    POLICY_ZERO_UNPAID,
                    STATUS_NONE,
                    effectiveCancelTime
            );
        }

        BigDecimal deposit = advanceDeposit != null && advanceDeposit.signum() > 0
                ? advanceDeposit
                : BigDecimal.ZERO;

        if (deposit.signum() == 0) {
            return new RefundDecision(
                    false,
                    BigDecimal.ZERO,
                    0,
                    POLICY_ZERO_UNPAID,
                    STATUS_NONE,
                    effectiveCancelTime
            );
        }

        LocalDateTime checkIn = expectedCheckInTime != null
                ? expectedCheckInTime
                : effectiveCancelTime.plusHours(3);
        LocalDateTime twoHoursBefore = checkIn.minusHours(2);

        if (!effectiveCancelTime.isAfter(twoHoursBefore)) {
            BigDecimal refund = deposit.setScale(0, RoundingMode.HALF_UP);
            boolean requested = refund.signum() > 0;
            return new RefundDecision(
                    requested,
                    refund,
                    100,
                    POLICY_FULL_100,
                    requested ? STATUS_REQUESTED : STATUS_NONE,
                    effectiveCancelTime
            );
        }

        if (effectiveCancelTime.isBefore(checkIn)) {
            BigDecimal refund = deposit.multiply(new BigDecimal("0.50")).setScale(0, RoundingMode.HALF_UP);
            boolean requested = refund.signum() > 0;
            return new RefundDecision(
                    requested,
                    refund,
                    50,
                    POLICY_PARTIAL_50,
                    requested ? STATUS_REQUESTED : STATUS_NONE,
                    effectiveCancelTime
            );
        }

        return new RefundDecision(
                false,
                BigDecimal.ZERO,
                0,
                POLICY_ZERO_AT_OR_AFTER_CHECKIN,
                STATUS_NONE,
                effectiveCancelTime
        );
    }

    public static RefundDecision evaluateNoShow(LocalDateTime cancelTime) {
        LocalDateTime effectiveCancelTime = cancelTime != null ? cancelTime : LocalDateTime.now(BUSINESS_ZONE);
        return new RefundDecision(
                false,
                BigDecimal.ZERO,
                0,
                POLICY_ZERO_NO_SHOW,
                STATUS_NONE,
                effectiveCancelTime
        );
    }
}

