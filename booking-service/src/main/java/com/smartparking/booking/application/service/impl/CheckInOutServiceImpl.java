package com.smartparking.booking.application.service.impl;

import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.booking.application.dto.CheckOutResponse;
import com.smartparking.booking.application.service.CheckInOutService;
import com.smartparking.booking.application.service.OutboxPublisherService;
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
        if (parkingLotId != null && booking.getParkingLotId() != null && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId != null && booking.getMerchantId() != null && !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền check-in cho đơn đặt chỗ của bãi xe này!");
        }

        if (booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái chờ check-in!");
        }

        LocalDateTime now = LocalDateTime.now();
        if (booking.getExpectedCheckInTime() != null && now.isBefore(booking.getExpectedCheckInTime().minusMinutes(checkInEarlyMinutes))) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chưa đến giờ check-in (chỉ được vào bãi trước tối đa " + checkInEarlyMinutes + " phút)!");
        }
        if (booking.getExpiresAt() != null && booking.getExpiresAt().isBefore(now)) {
            throw new AppException(ErrorCode.BOOKING_HOLD_EXPIRED, "QR Code đặt chỗ đã hết hạn!");
        }

        booking.setStatus(BookingStatusEnum.PARKING);
        booking.setCheckInTime(now);
        bookingRepository.save(booking);

        outboxPublisherService.publishBookingCheckedIn(booking);
        log.info("Booking {} checked-in successfully at lot {}", bookingCode, parkingLotId);
    }

    @Override
    @Transactional
    public CheckOutResponse checkOut(Long merchantUserId, String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        // Multi-tenant validation
        if (parkingLotId != null && booking.getParkingLotId() != null && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId != null && booking.getMerchantId() != null && !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền check-out cho đơn đặt chỗ của bãi xe này!");
        }

        if (booking.getStatus() != BookingStatusEnum.PARKING) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái đang đỗ!");
        }

        LocalDateTime checkOutTime = LocalDateTime.now();
        booking.setCheckOutTime(checkOutTime);

        long minutes = Math.max(0, Duration.between(
                booking.getCheckInTime() != null ? booking.getCheckInTime() : checkOutTime.minusHours(1),
                checkOutTime).toMinutes());
        long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));

        // Use dynamic hourly rate configured for the lot, fallback to 20,000 VND / hour
        BigDecimal hourlyRate = booking.getHourlyRate() != null && booking.getHourlyRate().compareTo(BigDecimal.ZERO) > 0
                ? booking.getHourlyRate()
                : BigDecimal.valueOf(20000L);
        BigDecimal totalAmount = hourlyRate.multiply(BigDecimal.valueOf(hours));
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
                parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
                queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
            } catch (Exception ex) {
                log.warn("Failed to release slot on checkout: {}", ex.getMessage());
            }
            outboxPublisherService.publishBookingCompleted(booking);
        } else {
            booking.setStatus(BookingStatusEnum.AWAITING_FINAL_PAYMENT);
        }

        bookingRepository.save(booking);
        log.info("Booking {} check-out processed. Total={}, Deposit={}, Due={}, Status={}",
                bookingCode, totalAmount, deposit, amountDue, booking.getStatus());

        return CheckOutResponse.builder()
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
    public void confirmCashPayment(Long merchantUserId, String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        if (booking.getStatus() != BookingStatusEnum.AWAITING_FINAL_PAYMENT) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái chờ thanh toán phụ phí!");
        }

        if (parkingLotId != null && booking.getParkingLotId() != null && !parkingLotId.equals(booking.getParkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã đặt chỗ này thuộc về bãi đỗ xe khác!");
        }
        if (merchantUserId != null && booking.getMerchantId() != null && !merchantUserId.equals(booking.getMerchantId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xác nhận thu tiền cho bãi xe này!");
        }

        booking.setStatus(BookingStatusEnum.COMPLETED);
        bookingRepository.save(booking);

        try {
            SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                    .vehicleType(booking.getVehicleType())
                    .bookingId(booking.getId())
                    .build();
            parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
            queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
        } catch (Exception ex) {
            log.warn("Failed to release slot on cash checkout: {}", ex.getMessage());
        }

        outboxPublisherService.publishBookingCompleted(booking);
        log.info("Booking {} completed via cash payment at gate by merchant {}", bookingCode, merchantUserId);
    }
}
