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

import static com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final com.smartparking.booking.application.service.UnpaidHoldPolicy holdPolicy;
    private final com.smartparking.booking.application.service.HoldRateLimiter holdRateLimiter;
    private final com.smartparking.booking.application.service.BookingCreationTransaction creationTransaction;
    private final com.smartparking.booking.infrastructure.persistence.HoldPolicyStore holdPolicyStore;
    private final com.smartparking.booking.infrastructure.client.OwnedVehicleClient ownedVehicleClient;
    private final ParkingLotClient parkingLotClient;
    private final OutboxPublisherService outboxPublisherService;
    private final com.smartparking.booking.application.service.QueueService queueService;

    @Override
    public BookingDto createBooking(Long driverUserId, CreateBookingRequest request) {
        String requestKey = request.getRequestId() == null ? null : driverUserId + ":" + request.getRequestId();
        if (requestKey != null) {
            var previous = bookingRepository.findByRequestKey(requestKey);
            if (previous.isPresent()) {
                Booking b = previous.get();
                boolean sameLot = b.getParkingLotId().equals(request.getParkingLotId());
                boolean sameVehicle = b.getVehicleId().equals(request.getVehicleId());
                boolean sameType = b.getVehicleType() == request.getVehicleType();
                boolean sameCheckIn = java.util.Objects.equals(b.getExpectedCheckInTime(), request.getExpectedCheckInTime());
                boolean sameCheckOut = java.util.Objects.equals(b.getExpectedCheckOutTime(), request.getExpectedCheckOutTime());
                boolean sameSlot = (request.getPhysicalSlotId() == null)
                        || java.util.Objects.equals(request.getPhysicalSlotId(), b.getPhysicalSlotId());
                if (!sameLot || !sameVehicle || !sameType || !sameCheckIn || !sameCheckOut || !sameSlot) {
                    throw new AppException(ErrorCode.CONFLICT, "Yêu cầu trùng lặp nhưng nội dung đặt chỗ đã bị thay đổi");
                }
                return BookingDto.fromEntity(b);
            }
        }
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        holdRateLimiter.acquire(driverUserId, now);
        if (!request.getExpectedCheckOutTime().isAfter(request.getExpectedCheckInTime()) || request.getExpectedCheckInTime().isBefore(now))
            throw new AppException(ErrorCode.BAD_REQUEST, "Giờ vào phải ở tương lai và giờ ra phải sau giờ vào");
        var vehicle = ownedVehicleClient.require(request.getVehicleId());
        if (vehicle.vehicleType() != request.getVehicleType())
            throw new AppException(ErrorCode.BAD_REQUEST, "Loại xe không khớp phương tiện đã đăng ký");
        return creationTransaction.run(() -> {
        holdPolicy.lock(driverUserId, request.getVehicleId());
        if (requestKey != null) {
            var concurrent = bookingRepository.findByRequestKeyForUpdate(requestKey);
            if (concurrent.isPresent()) {
                Booking b = concurrent.get();
                if (!java.util.Objects.equals(b.getParkingLotId(), request.getParkingLotId())
                        || !java.util.Objects.equals(b.getVehicleId(), request.getVehicleId())
                        || b.getVehicleType() != request.getVehicleType()
                        || !java.util.Objects.equals(b.getExpectedCheckInTime(), request.getExpectedCheckInTime())
                        || !java.util.Objects.equals(b.getExpectedCheckOutTime(), request.getExpectedCheckOutTime())
                        || (request.getPhysicalSlotId()!=null && !java.util.Objects.equals(b.getPhysicalSlotId(), request.getPhysicalSlotId())))
                    throw new AppException(ErrorCode.CONFLICT, "Yêu cầu trùng lặp nhưng nội dung đặt chỗ đã bị thay đổi");
                return BookingDto.fromEntity(b);
            }
        }
        holdPolicy.check(driverUserId, request.getVehicleId(), request.getParkingLotId(),
                LocalDateTime.now(BUSINESS_ZONE));
        String bookingCode = "BK" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        Booking booking = Booking.builder()
                .bookingCode(bookingCode).requestKey(requestKey)
                .driverId(driverUserId)
                .vehicleId(request.getVehicleId())
                .parkingLotId(request.getParkingLotId())
                .merchantId(null)
                .vehicleType(request.getVehicleType())
                .vehiclePlate(vehicle.licensePlate())
                .parkingLotName(request.getParkingLotName())
                .parkingLotAddress(request.getParkingLotAddress())
                .status(BookingStatusEnum.PENDING_PAYMENT)
                .expectedCheckInTime(request.getExpectedCheckInTime())
                .expectedCheckOutTime(request.getExpectedCheckOutTime())
                .expiresAt(now.plusMinutes(15))
                .build();

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            if (requestKey != null) {
                var recheck = bookingRepository.findByRequestKey(requestKey);
                if (recheck.isPresent()) {
                    Booking b = recheck.get();
                    boolean sameLot = b.getParkingLotId().equals(request.getParkingLotId());
                    boolean sameVehicle = b.getVehicleId().equals(request.getVehicleId());
                    boolean sameType = b.getVehicleType() == request.getVehicleType();
                    boolean sameCheckIn = java.util.Objects.equals(b.getExpectedCheckInTime(), request.getExpectedCheckInTime());
                    boolean sameCheckOut = java.util.Objects.equals(b.getExpectedCheckOutTime(), request.getExpectedCheckOutTime());
                    boolean sameSlot = (request.getPhysicalSlotId() == null)
                            || java.util.Objects.equals(request.getPhysicalSlotId(), b.getPhysicalSlotId());
                    if (!sameLot || !sameVehicle || !sameType || !sameCheckIn || !sameCheckOut || !sameSlot) {
                        throw new AppException(ErrorCode.CONFLICT, "Yêu cầu trùng lặp nhưng nội dung đặt chỗ đã bị thay đổi");
                    }
                    return BookingDto.fromEntity(b);
                }
            }
            throw new AppException(ErrorCode.CONFLICT, "Xung đột tạo đặt chỗ đồng thời");
        }

        // Call parking-lot-service to hold slot & lấy đơn giá giờ chính thức từ backend
        try {
            SlotHoldRequest holdReq = SlotHoldRequest.builder()
                    .parkingLotId(request.getParkingLotId())
                    .vehicleType(request.getVehicleType())
                    .bookingId(saved.getId())
                    .physicalSlotId(request.getPhysicalSlotId())
                    .build();

            ApiResponse<SlotHoldResponse> holdRes = parkingLotClient.holdSlot(request.getParkingLotId(), holdReq);
            if (holdRes == null || holdRes.getData() == null || !holdRes.getData().isSuccess()) {
                throw new AppException(ErrorCode.SLOT_NOT_AVAILABLE, "Hết chỗ đỗ cho loại xe này tại bãi đỗ đã chọn!");
            }
            var quote = holdRes.getData();
            if (quote.getMerchantId() == null || quote.getBaseHourlyPrice() == null || quote.getBaseHourlyPrice().signum() <= 0)
                throw new AppException(ErrorCode.BAD_REQUEST, "Không xác minh được chủ bãi hoặc bảng giá");
            saved.setMerchantId(quote.getMerchantId()); saved.setParkingLotName(quote.getParkingLotName());
            saved.setParkingLotAddress(quote.getParkingLotAddress()); saved.setHourlyRate(quote.getBaseHourlyPrice());
            saved.setSurgeMultiplier(quote.getSurgeMultiplier()); saved.setSurgeStartTime(quote.getSurgeStartTime());
            saved.setSurgeEndTime(quote.getSurgeEndTime()); saved.setOvertimePenaltyMultiplier(quote.getOvertimePenaltyMultiplier());
            saved.setPhysicalSlotId(quote.getPhysicalSlotId());
            saved.setPhysicalSlotCode(quote.getPhysicalSlotCode());
            saved.setLayoutPublicationId(quote.getLayoutPublicationId());
            BigDecimal estimate = com.smartparking.booking.domain.ParkingCharge.charge(request.getExpectedCheckInTime(),
                    request.getExpectedCheckOutTime(), saved.getHourlyRate(), saved.getSurgeMultiplier(), saved.getSurgeStartTime(), saved.getSurgeEndTime());
            saved.setTotalAmount(estimate); saved.setAdvanceDeposit(com.smartparking.booking.domain.ParkingCharge.deposit(estimate));
            saved = bookingRepository.save(saved);
        } catch (AppException e) {
            releaseFailedHold(request, saved.getId());
            throw e;
        } catch (feign.FeignException.BadRequest | feign.FeignException.Conflict e) {
            releaseFailedHold(request, saved.getId());
            log.error("Slot hold rejected by parking-lot-service: {}", e.getMessage());
            throw new AppException(ErrorCode.SLOT_NOT_AVAILABLE, "Hết chỗ đỗ cho loại xe này tại bãi đỗ đã chọn!");
        } catch (feign.FeignException.NotFound e) {
            releaseFailedHold(request, saved.getId());
            log.error("Parking lot not found: {}", e.getMessage());
            throw new AppException(ErrorCode.PARKING_LOT_NOT_FOUND, "Bãi đỗ xe không tồn tại hoặc đã ngừng hoạt động!");
        } catch (feign.FeignException e) {
            releaseFailedHold(request, saved.getId());
            log.error("Feign call to parking-lot-service failed with HTTP {}: {}", e.status(), e.getMessage());
            String detail = "Không thể kết nối đến hệ thống bãi đỗ xe (cổng 8082), vui lòng đảm bảo parking-lot-service đang chạy!";
            if (e.contentUTF8() != null && !e.contentUTF8().isBlank()) {
                detail = e.contentUTF8();
            }
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, detail);
        } catch (Exception e) {
            releaseFailedHold(request, saved.getId());
            log.error("Unexpected error while holding slot: {}", e.getMessage());
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi hệ thống khi giữ chỗ đỗ xe!");
        }

        outboxPublisherService.publishBookingCreated(saved);
        log.info("Booking created: code={} driver={} lot={}", saved.getBookingCode(), driverUserId, request.getParkingLotId());

        return BookingDto.fromEntity(saved);
        });
    }

    private void releaseFailedHold(CreateBookingRequest request, Long bookingId) {
        try { parkingLotClient.releaseSlot(request.getParkingLotId(), SlotHoldRequest.builder()
                .bookingId(bookingId).vehicleType(request.getVehicleType()).build()); }
        catch (Exception failure) { log.error("Slot hold compensation requires reconciliation: booking={} lot={}", bookingId, request.getParkingLotId(), failure); }
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
    public PageResponse<BookingDto> getMerchantBookings(Long merchantUserId, Long parkingLotId, Pageable pageable) {
        Page<Booking> page;
        if (parkingLotId != null) {
            page = bookingRepository.findByMerchantIdAndParkingLotIdOrderByCreatedAtDesc(merchantUserId, parkingLotId, pageable);
        } else {
            page = bookingRepository.findByMerchantIdOrderByCreatedAtDesc(merchantUserId, pageable);
        }
        return new PageResponse<>(page.map(BookingDto::fromEntity));
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
    @Transactional(readOnly = true)
    public BookingDto getMerchantBookingDetail(Long merchantUserId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));

        if (booking.getMerchantId() == null || !booking.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập thông tin booking này");
        }

        return BookingDto.fromEntity(booking);
    }

    @Override
    @Transactional
    public CancelBookingResponse cancelBooking(Long driverUserId, Long bookingId, CancelBookingRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND, "Không tìm thấy booking"));

        if (!booking.getDriverId().equals(driverUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy booking này");
        }

        if (booking.getStatus() != BookingStatusEnum.PENDING_PAYMENT && booking.getStatus() != BookingStatusEnum.PENDING_CHECKIN) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID, "Chỉ có thể hủy booking chưa check-in");
        }

        com.smartparking.booking.domain.BookingRefundPolicy.RefundDecision refundDecision =
                com.smartparking.booking.domain.BookingRefundPolicy.evaluateDriverCancel(
                        booking.getStatus(),
                        booking.getAdvanceDeposit(),
                        booking.getExpectedCheckInTime(),
                        LocalDateTime.now(com.smartparking.booking.domain.BookingRefundPolicy.BUSINESS_ZONE)
                );

        boolean wasUnpaid = booking.getStatus() == BookingStatusEnum.PENDING_PAYMENT;
        String reason = request != null ? request.getReason() : null;
        booking.setStatus(BookingStatusEnum.CANCELLED);
        booking.setCancelledAt(refundDecision.cancelledAt());
        booking.setCancellationReason(reason);
        booking.setRefundAmount(refundDecision.refundAmount());
        booking.setRefundPercentage(refundDecision.refundPercentage());
        booking.setRefundPolicy(refundDecision.refundPolicy());
        booking.setRefundStatus(refundDecision.refundStatus());
        bookingRepository.save(booking);
        if (wasUnpaid) holdPolicyStore.recordRelease(booking, "DRIVER_CANCELLED", refundDecision.cancelledAt());
        outboxPublisherService.publishBookingCancelled(booking, reason);

        // Notify parking-lot-service to release slot
        try {
            SlotHoldRequest releaseReq = SlotHoldRequest.builder()
                    .vehicleType(booking.getVehicleType())
                    .bookingId(booking.getId())
                    .build();
            parkingLotClient.releaseSlot(booking.getParkingLotId(), releaseReq);
            queueService.promoteNextInQueue(booking.getParkingLotId(), booking.getVehicleType());
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Chưa hủy được chỗ đỗ. Vui lòng thử lại.");
        }

        log.info("Booking {} cancelled by driver {}: refundRequested={} refundAmount={} policy={}",
                booking.getBookingCode(), driverUserId, refundDecision.refundRequested(),
                refundDecision.refundAmount(), refundDecision.refundPolicy());

        return CancelBookingResponse.builder()
                .bookingId(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status("CANCELLED")
                .refundRequested(refundDecision.refundRequested())
                .refundAmount(refundDecision.refundAmount())
                .refundPercentage(refundDecision.refundPercentage())
                .refundPolicy(refundDecision.refundPolicy())
                .refundStatus(refundDecision.refundStatus())
                .message("Hủy đặt chỗ thành công")
                .build();
    }
}
