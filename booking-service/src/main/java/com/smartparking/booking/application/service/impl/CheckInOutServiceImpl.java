package com.smartparking.booking.application.service.impl;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.util.BookingSchedulePolicy;
import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.domain.BookingTimePolicy;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInOutServiceImpl implements CheckInOutService {

    private final BookingRepository bookingRepository;
    private final ParkingLotClient parkingLotClient;
    private final OutboxPublisherService outboxPublisherService;
    private final com.smartparking.booking.application.service.QueueService queueService;

    @Value("${app.booking.check-in-early-minutes:30}")
    private int checkInEarlyMinutes;

    @Override
    @Transactional
    public void checkIn(Long merchantUserId, String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        // Multi-tenant validation: prevent scanning booking for another lot or merchant
        if (parkingLotId != null && booking.getParkingLotId() != null
                && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId == null || booking.getMerchantId() == null || !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền check-in cho đơn đặt chỗ của bãi xe này!");
        }

        if (booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái chờ check-in!");
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        int earlyMinutes = BookingSchedulePolicy.checkInEarlyMinutes(booking.getSchedulePolicyVersion(), checkInEarlyMinutes);
        LocalDateTime opensAt = BookingTimePolicy.checkInOpensAt(booking.getExpectedCheckInTime(), earlyMinutes);
        if (opensAt == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không xác định được giờ check-in của lượt đặt chỗ này.");
        }
        if (now.isBefore(opensAt)) {
            String remaining = formatCheckInWait(Duration.between(now, opensAt));
            throw new AppException(ErrorCode.BAD_REQUEST, "Còn " + remaining + " đến giờ check-in.");
        }
        if (booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.BOOKING_HOLD_EXPIRED, "QR Code đặt chỗ đã hết hạn!");
        }

        if (BookingSchedulePolicy.hasScheduledWindow(booking.getSchedulePolicyVersion()) && !now.isBefore(booking.getExpectedCheckOutTime()))
            throw new AppException(ErrorCode.BOOKING_HOLD_EXPIRED, "Đã hết khung giờ đặt chỗ");
        com.smartparking.booking.application.service.ScheduledSlotLifecycle.occupy(booking, parkingLotClient);
        booking.setStatus(BookingStatusEnum.PARKING);
        booking.setCheckInTime(now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        bookingRepository.save(booking);

        outboxPublisherService.publishBookingCheckedIn(booking);
        log.info("Booking {} checked-in successfully at lot {}", bookingCode, parkingLotId);
    }

    private static String formatCheckInWait(Duration wait) {
        long hours = wait.toHours();
        int minutes = wait.toMinutesPart();
        if (hours == 0) return minutes == 0 ? "dưới 1 phút" : minutes + " phút";
        return hours + " giờ" + (minutes == 0 ? "" : " " + minutes + " phút");
    }

    @Override
    @Transactional
    public CheckOutResponse checkOut(Long merchantUserId, String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        // Multi-tenant validation
        if (parkingLotId != null && booking.getParkingLotId() != null
                && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId == null || booking.getMerchantId() == null || !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền check-out cho đơn đặt chỗ của bãi xe này!");
        }

        if (booking.getStatus() == BookingStatusEnum.AWAITING_FINAL_PAYMENT
                || booking.getStatus() == BookingStatusEnum.COMPLETED) {
            BigDecimal totalAmount = booking.getTotalAmount() != null ? booking.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal deposit = booking.getAdvanceDeposit() != null ? booking.getAdvanceDeposit() : BigDecimal.ZERO;
            BigDecimal amountDue = booking.getStatus() == BookingStatusEnum.COMPLETED ? BigDecimal.ZERO
                    : totalAmount.subtract(deposit).max(BigDecimal.ZERO);
            long minutes = Math.max(0, Duration.between(
                    booking.getCheckInTime() != null ? booking.getCheckInTime() : LocalDateTime.now(BUSINESS_ZONE).minusHours(1),
                    booking.getCheckOutTime() != null ? booking.getCheckOutTime() : LocalDateTime.now(BUSINESS_ZONE)).toMinutes());

            return CheckOutResponse.builder()
                    .bookingId(booking.getId())
                    .finalPaymentMethod(booking.getFinalPaymentMethod())
                    .bookingCode(bookingCode)
                    .totalAmount(totalAmount)
                    .advanceDeposit(deposit)
                    .amountDue(amountDue)
                    .parkingMinutes(minutes)
                    .bookingStatus(booking.getStatus().name())
                    .build();
        }

        if (booking.getStatus() != BookingStatusEnum.PARKING && booking.getStatus() != BookingStatusEnum.OVERTIME) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái đang đỗ!");
        }

        LocalDateTime checkOutTime = LocalDateTime.now(BUSINESS_ZONE).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        booking.setCheckOutTime(checkOutTime);
        com.smartparking.booking.application.service.ScheduledSlotLifecycle.checkoutAfterCommit(booking, parkingLotClient);

        long minutes = Math.max(0, Duration.between(
                booking.getCheckInTime() != null ? booking.getCheckInTime() : checkOutTime.minusHours(1),
                checkOutTime).toMinutes());

        if (booking.getCheckInTime() == null || booking.getHourlyRate() == null || booking.getHourlyRate().signum() <= 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Booking thiếu dữ liệu tính phí; cần đối soát trước khi ra bãi");
        LocalDateTime start = booking.getCheckInTime();
        // Entry starts the first billing hour even when exit is recorded in the same instant.
        LocalDateTime chargeUntil = checkOutTime.isAfter(start) ? checkOutTime : start.plusNanos(1);
        LocalDateTime regularEnd = booking.getExpectedCheckOutTime() != null && booking.getExpectedCheckOutTime().isBefore(chargeUntil)
                ? booking.getExpectedCheckOutTime() : chargeUntil;
        BigDecimal regular = com.smartparking.booking.domain.ParkingCharge.charge(start, regularEnd,
                booking.getHourlyRate(), booking.getSurgeMultiplier(), booking.getSurgeStartTime(), booking.getSurgeEndTime());
        // Settle at least the full reserved fee at checkout, even for late arrival or early departure.
        if (booking.getExpectedCheckInTime() != null && booking.getExpectedCheckOutTime() != null) {
            BigDecimal reserved = com.smartparking.booking.domain.ParkingCharge.charge(
                    booking.getExpectedCheckInTime(), booking.getExpectedCheckOutTime(),
                    booking.getHourlyRate(), booking.getSurgeMultiplier(), booking.getSurgeStartTime(), booking.getSurgeEndTime());
            regular = regular.max(reserved);
        }
        BigDecimal overtime = BigDecimal.ZERO;
        if (booking.getExpectedCheckOutTime() != null && chargeUntil.isAfter(booking.getExpectedCheckOutTime())) {
            LocalDateTime overtimeStart = start.isAfter(booking.getExpectedCheckOutTime()) ? start : booking.getExpectedCheckOutTime();
            BigDecimal penalty = booking.getOvertimePenaltyMultiplier() != null ? booking.getOvertimePenaltyMultiplier() : BigDecimal.ONE;
            overtime = com.smartparking.booking.domain.ParkingCharge.chargeOvertime(overtimeStart, chargeUntil,
                    booking.getHourlyRate().multiply(penalty), booking.getSurgeMultiplier(), booking.getSurgeStartTime(), booking.getSurgeEndTime());
        }
        BigDecimal totalAmount = regular.add(overtime);
        BigDecimal deposit = booking.getAdvanceDeposit() != null ? booking.getAdvanceDeposit() : BigDecimal.ZERO;
        BigDecimal amountDue = totalAmount.subtract(deposit).max(BigDecimal.ZERO);

        booking.setTotalAmount(totalAmount);

        if (amountDue.compareTo(BigDecimal.ZERO) <= 0) {
            booking.setStatus(BookingStatusEnum.COMPLETED);
            try {
                SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                        .vehicleType(booking.getVehicleType())
                        .bookingId(booking.getId())
                        .build();
                if (booking.getSchedulePolicyVersion() == null) parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
                if (booking.getSchedulePolicyVersion() == null) queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
            } catch (Exception ex) {
                throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa giải phóng được chỗ đỗ. Vui lòng thử lại.");
            }
            outboxPublisherService.publishBookingCompleted(booking);
        } else {
            booking.setStatus(BookingStatusEnum.AWAITING_FINAL_PAYMENT);
        }

        bookingRepository.save(booking);
        log.info("Booking {} check-out processed. Total={}, Deposit={}, Due={}, Status={}",
                bookingCode, totalAmount, deposit, amountDue, booking.getStatus());

        return CheckOutResponse.builder()
                .bookingId(booking.getId())
                .finalPaymentMethod(booking.getFinalPaymentMethod())
                .bookingCode(bookingCode)
                .totalAmount(totalAmount)
                .advanceDeposit(deposit)
                .amountDue(amountDue)
                .parkingMinutes(minutes)
                .bookingStatus(booking.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public void confirmCashPayment(Long merchantUserId, String bookingCode, Long parkingLotId, BigDecimal amount) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        if (booking.getStatus() != BookingStatusEnum.AWAITING_FINAL_PAYMENT
                && !(booking.getStatus() == BookingStatusEnum.COMPLETED && "CASH".equals(booking.getFinalPaymentMethod()))) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID,
                    "Booking không ở trạng thái chờ thanh toán phụ phí!");
        }

        if (parkingLotId != null && booking.getParkingLotId() != null
                && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId == null || booking.getMerchantId() == null || !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xác nhận thu tiền cho bãi xe này!");
        }

        BigDecimal due = booking.getTotalAmount() != null && booking.getAdvanceDeposit() != null
                ? booking.getTotalAmount().subtract(booking.getAdvanceDeposit()).max(BigDecimal.ZERO) : null;
        if (amount == null || amount.signum() <= 0 || due == null || amount.compareTo(due) != 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền mặt không khớp số tiền còn phải thu");
        }
        if (booking.getStatus() == BookingStatusEnum.COMPLETED) return;
        if (booking.getFinalPaymentMethod() != null) {
            throw new AppException(ErrorCode.CONFLICT, "Đã ghi nhận thanh toán qua kênh khác; vui lòng kiểm tra lại");
        }
        booking.setFinalPaymentMethod("CASH");
        booking.setFinalPaymentReference("CASH:" + booking.getId());
        booking.setFinalPaymentAmount(amount);
        booking.setStatus(BookingStatusEnum.COMPLETED);
        bookingRepository.save(booking);

        try {
            SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                    .vehicleType(booking.getVehicleType())
                    .bookingId(booking.getId())
                    .build();
            if (booking.getSchedulePolicyVersion() == null) parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
            if (booking.getSchedulePolicyVersion() == null) queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa giải phóng được chỗ đỗ. Vui lòng thử lại.");
        }

        outboxPublisherService.publishBookingCompleted(booking);
        log.info("Booking {} completed via cash payment at gate by merchant {}. Amount={}", bookingCode, merchantUserId, amount);
    }
}
