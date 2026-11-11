package com.smartparking.payment.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.DisputeStatusEnum;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.common.security.SecurityConstants;
import com.smartparking.common.util.Pageables;
import com.smartparking.payment.application.dto.AdminDisputeResponse;
import com.smartparking.payment.application.dto.CreateDisputeRequest;
import com.smartparking.payment.application.dto.CreateDisputeResponseRequest;
import com.smartparking.payment.application.dto.DisputeDetailDto;
import com.smartparking.payment.application.dto.DisputeDto;
import com.smartparking.payment.application.dto.DisputeTimelineEntryDto;
import com.smartparking.payment.application.dto.DriverDisputeStatus;
import com.smartparking.payment.application.dto.LegacyDisputeDto;
import com.smartparking.payment.application.service.DisputeService;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.Dispute;
import com.smartparking.payment.domain.model.DisputeHistory;
import com.smartparking.payment.domain.model.DisputeHistoryEventType;
import com.smartparking.payment.domain.repository.DisputeHistoryRepository;
import com.smartparking.payment.domain.repository.DisputeRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.infrastructure.client.DisputeBookingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DisputeServiceImpl implements DisputeService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE = 10_000;
    private static final int MAX_SIZE = 100;

    private final DisputeRepository disputeRepository;
    private final DisputeHistoryRepository disputeHistoryRepository;
    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;
    private final DisputeBookingClient disputeBookingClient;
    private final DisputeWriteService disputeWriteService;

    @Override
    public DisputeDto createDispute(Long driverUserId, CreateDisputeRequest request) {
        DisputeBookingClient.VerifiedBooking booking =
                disputeBookingClient.verifyBookingOwnership(request.getBookingId(), driverUserId);

        if (request.getParkingLotId() != null && !request.getParkingLotId().equals(booking.parkingLotId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "parkingLotId không khớp với lượt đặt chỗ");
        }

        List<String> evidenceUrls = normalizeEvidenceUrls(request.getEvidenceUrls());
        Dispute saved = disputeWriteService.createDisputeRecord(
                driverUserId,
                booking.bookingId(),
                booking.merchantId(),
                booking.parkingLotId(),
                request.getReason().trim(),
                request.getDescription(),
                evidenceUrls
        );
        return DisputeDto.fromEntity(saved, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DisputeDto> getMyDisputes(Long driverUserId, Integer page, Integer size, String status) {
        DriverDisputeStatus statusFilter = status == null ? null : DriverDisputeStatus.fromStrictParam(status);
        Pageable sanitized = sanitizeDriverPageable(page, size);

        Page<Dispute> resultPage;
        if (statusFilter == null) {
            resultPage = disputeRepository.findAllDriverDisputesOrdered(driverUserId, sanitized);
        } else {
            resultPage = switch (statusFilter) {
                case OPEN -> disputeRepository.findDriverOpenDisputesOrdered(driverUserId, sanitized);
                case IN_REVIEW -> disputeRepository.findDriverInReviewDisputesOrdered(driverUserId, sanitized);
                case RESOLVED -> disputeRepository.findDriverDisputesByStatusOrdered(
                        driverUserId, DisputeStatusEnum.RESOLVED, sanitized);
                case REJECTED -> disputeRepository.findDriverDisputesByStatusOrdered(
                        driverUserId, DisputeStatusEnum.REJECTED, sanitized);
            };
        }

        List<Long> pendingIds = resultPage.getContent().stream()
                .filter(d -> d.getStatus() == DisputeStatusEnum.PENDING)
                .map(Dispute::getId)
                .filter(Objects::nonNull)
                .toList();

        Set<Long> inReviewIds = pendingIds.isEmpty()
                ? Collections.emptySet()
                : disputeHistoryRepository.findDisputeIdsWithEventType(
                        pendingIds, DisputeHistoryEventType.MERCHANT_RESPONSE);

        Page<DisputeDto> dtoPage = resultPage.map(d ->
                DisputeDto.fromEntity(d, d.getId() != null && inReviewIds.contains(d.getId())));
        return new PageResponse<>(dtoPage);
    }

    @Override
    @Transactional(readOnly = true)
    public DisputeDetailDto getMyDisputeDetail(Long driverUserId, Long id) {
        Dispute dispute = disputeRepository.findByIdAndDriverId(id, driverUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));

        List<DisputeHistory> history = disputeHistoryRepository.findByDisputeIdOrderByCreatedAtAscIdAsc(dispute.getId());
        boolean hasMerchantResponse = history.stream()
                .anyMatch(h -> h.getEventType() == DisputeHistoryEventType.MERCHANT_RESPONSE);
        List<DisputeTimelineEntryDto> timeline = history.stream()
                .map(DisputeTimelineEntryDto::fromEntity)
                .toList();

        return DisputeDetailDto.fromEntity(dispute, hasMerchantResponse, timeline);
    }

    @Override
    @Transactional
    public DisputeTimelineEntryDto addMerchantResponse(
            Long merchantUserId,
            Long id,
            CreateDisputeResponseRequest request) {
        Dispute dispute = disputeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));

        if (dispute.getMerchantId() == null || !dispute.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền phản hồi khiếu nại này");
        }

        if (dispute.getStatus() != DisputeStatusEnum.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Khiếu nại đã được xử lý, không thể gửi phản hồi");
        }

        DisputeHistory saved = disputeHistoryRepository.save(DisputeHistory.builder()
                .disputeId(dispute.getId())
                .eventType(DisputeHistoryEventType.MERCHANT_RESPONSE)
                .actorUserId(merchantUserId)
                .actorRole("MERCHANT")
                .message(request.getMessage().trim())
                .resultingStatus(DriverDisputeStatus.IN_REVIEW.name())
                .build());

        log.info("Merchant {} responded to dispute {}", merchantUserId, id);
        return DisputeTimelineEntryDto.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LegacyDisputeDto> getMerchantDisputes(Long merchantUserId, Pageable pageable) {
        Page<LegacyDisputeDto> page = disputeRepository.findByMerchantIdOrderByCreatedAtDesc(merchantUserId, pageable)
                .map(LegacyDisputeDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyDisputeDto getDisputeDetail(Long userId, String role, Long id) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));
        boolean isDriver = SecurityConstants.ROLE_DRIVER.equals(role) || "DRIVER".equals(role);
        boolean isMerchant = SecurityConstants.ROLE_MERCHANT.equals(role) || "MERCHANT".equals(role);
        boolean allowed = (isDriver && dispute.getDriverId().equals(userId))
                || (isMerchant && dispute.getMerchantId() != null && dispute.getMerchantId().equals(userId));
        if (!allowed) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền truy cập khiếu nại này");
        }
        return LegacyDisputeDto.fromEntity(dispute);
    }

    @Override
    @Transactional
    public AdminDisputeResponse resolveDispute(Long adminUserId, Long id, String resolution) {
        Dispute dispute = disputeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));

        dispute.setStatus(DisputeStatusEnum.RESOLVED);
        dispute.setResolution(resolution);
        Dispute saved = disputeRepository.save(dispute);

        // Execute financial adjustment if resolution requires refund
        if (resolution != null && resolution.toUpperCase().contains("REFUND")) {
            paymentRepository.findByBookingId(dispute.getBookingId()).stream()
                    .filter(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)
                    .forEach(payment -> {
                        payment.setStatus(PaymentStatusEnum.REFUNDED);
                        paymentRepository.save(payment);
                        log.info("Payment {} marked REFUNDED due to dispute resolution #{}", payment.getId(), id);

                        if (dispute.getMerchantId() != null) {
                            merchantWalletService.recordRefundLiability(
                                    dispute.getMerchantId(),
                                    dispute.getBookingId(),
                                    payment.getId(),
                                    payment.getAmount(),
                                    "DISPUTE_REFUND:" + id + ":PAYMENT:" + payment.getId(),
                                    "Thu hồi doanh thu do giải quyết khiếu nại #" + id + ": " + resolution);
                        }
                    });
        }

        disputeHistoryRepository.save(DisputeHistory.builder()
                .disputeId(saved.getId())
                .eventType(DisputeHistoryEventType.ADMIN_RESOLUTION)
                .actorUserId(adminUserId)
                .actorRole("ADMIN")
                .message(resolution != null ? resolution.trim() : "")
                .resultingStatus(DriverDisputeStatus.RESOLVED.name())
                .build());

        log.info("Admin {} resolved dispute {}", adminUserId, id);
        return AdminDisputeResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminDisputeResponse> getAdminDisputes(Pageable pageable) {
        Page<AdminDisputeResponse> page = disputeRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AdminDisputeResponse::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDisputeResponse getAdminDispute(Long id) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy khiếu nại"));
        return AdminDisputeResponse.fromEntity(dispute);
    }

    private Pageable sanitizeDriverPageable(Integer page, Integer size) {
        int safePage = (page == null || page < 0 || page > MAX_PAGE) ? DEFAULT_PAGE : page;
        int safeSize = (size == null || size < 1 || size > MAX_SIZE) ? DEFAULT_SIZE : size;
        return Pageables.sanitize(PageRequest.of(safePage, safeSize));
    }

    private List<String> normalizeEvidenceUrls(List<String> rawUrls) {
        if (rawUrls == null || rawUrls.isEmpty()) {
            return Collections.emptyList();
        }
        return rawUrls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .peek(DisputeEvidenceServiceImpl::extractInternalFileName)
                .distinct()
                .toList();
    }
}
