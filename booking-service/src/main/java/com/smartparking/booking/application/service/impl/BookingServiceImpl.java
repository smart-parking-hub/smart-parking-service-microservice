package com.smartparking.booking.application.service.impl;

import com.smartparking.common.dto.ApiResponse;
import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.BookingStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.booking.application.dto.BookingDto;
import com.smartparking.booking.application.dto.CancelBookingRequest;
import com.smartparking.booking.application.dto.CancelBookingResponse;
import com.smartparking.booking.application.dto.CreateBookingRequest;
import com.smartparking.booking.application.service.BookingService;
import com.smartparking.booking.application.service.OutboxPublisherService;
import com.smartparking.booking.domain.model.Booking;
import com.smartparking.booking.domain.repository.BookingRepository;
import com.smartparking.booking.infrastructure.client.ParkingLotClient;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldRequest;
import com.smartparking.booking.infrastructure.client.dto.SlotHoldResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final ParkingLotClient parkingLotClient;
    private final OutboxPublisherService outboxPublisherService;

    @Override
    @Transactional
    public BookingDto createBooking(Long driverUserId, CreateBookingRequest request) {
        if (request.getExpectedCheckInTime().isAfter(request.getExpectedCheckOutTime())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Giờ vào bãi phải trước giờ ra bãi!");
        }

        String bookingCode = "BK" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        BigDecimal deposit = BigDecimal.valueOf(50000); // Standard advance deposit

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .driverId(driverUserId)
                .vehicleId(request.getVehicleId())
                .parkingLotId(request.getParkingLotId())
                .vehicleType(request.getVehicleType())
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .advanceDeposit(deposit)
                .expectedCheckInTime(request.getExpectedCheckInTime())
                .expectedCheckOutTime(request.getExpectedCheckOutTime())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        Booking saved = bookingRepository.save(booking);

        // Call parking-lot-service to hold slot
        try {
            SlotHoldRequest holdReq = SlotHoldRequest.builder()
                    .vehicleType(request.getVehicleType())
                    .bookingId(saved.getId())
                    .build();

            ApiResponse<SlotHoldResponse> holdRes = parkingLotClient.holdSlot(request.getParkingLotId(), holdReq);
            if (holdRes == null || holdRes.getData() == null || !holdRes.getData().isSuccess()) {
                throw new AppException(ErrorCode.SLOT_NOT_AVAILABLE, "Hết chỗ đỗ cho loại xe này tại bãi đỗ đã chọn!");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Feign call to parking-lot-service failed or unavailable: {}", e.getMessage());
        }

        outboxPublisherService.publishBookingCreated(saved);
        log.info("Booking created: code={} driver={} lot={}", saved.getBookingCode(), driverUserId, request.getParkingLotId());

        return BookingDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingDto> getMyBookings(Long driverUserId, Pageable pageable) {
        Page<BookingDto> page = bookingRepository.findByDriverIdOrderByCreatedAtDesc(driverUserId, pageable)
                .map(BookingDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingDto> getMerchantBookings(Long merchantUserId, Pageable pageable) {
        Page<BookingDto> page = bookingRepository.findByMerchantIdOrderByCreatedAtDesc(merchantUserId, pageable)
                .map(BookingDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingDto getBookingDetail(Long userId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));

        if (!booking.getDriverId().equals(userId) && (booking.getMerchantId() == null || !booking.getMerchantId().equals(userId))) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập thông tin booking này");
        }

        return BookingDto.fromEntity(booking);
    }

    @Override
    @Transactional
    public CancelBookingResponse cancelBooking(Long driverUserId, Long bookingId, CancelBookingRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));

        if (!booking.getDriverId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy booking này");
        }

        if (booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT && booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Chỉ có thể hủy booking chưa check-in");
        }

        boolean refundRequested = booking.getStatus() == BookingStatusEnum.PENDING_CHECKIN;
        booking.setStatus(BookingStatusEnum.CANCELLED);
        bookingRepository.save(booking);

        // Notify parking-lot-service to release slot
        try {
            SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                    .vehicleType(booking.getVehicleType())
                    .bookingId(booking.getId())
                    .build();
            parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
        } catch (Exception ex) {
            log.warn("Failed to release slot in parking-lot-service: {}", ex.getMessage());
        }

        outboxPublisherService.publishBookingCancelled(booking, request.getReason());
        log.info("Booking {} cancelled by driver {}", booking.getBookingCode(), driverUserId);

        return CancelBookingResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status("CANCELLED")
                .refundRequested(refundRequested)
                .refundAmount(refundRequested ? booking.getAdvanceDeposit() : BigDecimal.ZERO)
                .message("Hủy đặt chỗ thành công")
                .build();
    }
}
