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

    @Value("${app.booking.check-in-early-minutes:30}")
    private int checkInEarlyMinutes;

    @Override
    @Transactional
    public void checkIn(Long merchantUserId, String bookingCode, Long parkingLotId) {
        Booking booking = bookingRepository.findByBookingCodeForUpdate(bookingCode)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Booking không tồn tại!"));

        if (booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái chờ check-in!");
        }

        LocalDateTime now = LocalDateTime.now();
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

        if (booking.getStatus() != BookingStatusEnum.PARKING) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Booking không ở trạng thái đang đỗ!");
        }

        LocalDateTime checkOutTime = LocalDateTime.now();
        booking.setCheckOutTime(checkOutTime);

        long minutes = Math.max(0, Duration.between(
                booking.getCheckInTime() != null ? booking.getCheckInTime() : checkOutTime.minusHours(1),
                checkOutTime).toMinutes());
        long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));

        // Base rate: 20,000 VND / hour
        BigDecimal totalAmount = BigDecimal.valueOf(hours * 20000L);
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
}
