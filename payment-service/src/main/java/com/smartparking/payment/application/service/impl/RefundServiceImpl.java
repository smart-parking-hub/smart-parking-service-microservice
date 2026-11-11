package com.smartparking.payment.application.service.impl;

import com.smartparking.common.dto.PageResponse;
import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.event.BookingEvent;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.RefundRequestDto;
import com.smartparking.payment.application.dto.SubmitRefundEvidenceRequest;
import com.smartparking.payment.application.dto.VerifyRefundEvidenceRequest;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.application.service.OutboxPublisherService;
import com.smartparking.payment.application.service.RefundService;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefundServiceImpl implements RefundService {

    public static final String STATUS_NONE = "NONE";
    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REQUIRES_RECONCILIATION = "REQUIRES_RECONCILIATION";

    private final RefundRequestRepository refundRequestRepository;
    private final PaymentRepository paymentRepository;
    private final MerchantWalletService merchantWalletService;
    private final OutboxPublisherService outboxPublisherService;

    @Override
    @Transactional
    public RefundRequestDto handleBookingCancellationRefund(BookingEvent.BookingCancelledEvent event) {
        if (event == null || event.getBookingId() == null) {
            return null;
        }

        BigDecimal refundAmount = event.getRefundAmount();
        if (Boolean.FALSE.equals(event.getRefundRequested()) || refundAmount == null || refundAmount.signum() <= 0) {
            log.info("Booking {} cancelled with zero refund eligibility (policy={}); no refund request created",
                    event.getBookingId(), event.getRefundPolicy());
            return null;
        }

        String idempotencyKey = "refund:booking:" + event.getBookingId();
        Optional<RefundRequest> existing = refundRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate cancellation refund event skipped for booking {}", event.getBookingId());
            return RefundRequestDto.fromEntity(existing.get());
        }

        Optional<Payment> paidPaymentOpt = paymentRepository.findByBookingId(event.getBookingId()).stream()
                .filter(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)
                .findFirst();

        LocalDateTime now = LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE);
        LocalDateTime dueAt = now.plusHours(24);
        String policyCode = event.getRefundPolicy() != null ? event.getRefundPolicy() : "FULL_100_AT_OR_BEFORE_2H";
        int percentage = event.getRefundPercentage() != null ? event.getRefundPercentage() : 100;

        if (paidPaymentOpt.isEmpty()) {
            RefundRequest unverified = RefundRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .bookingId(event.getBookingId())
                    .driverId(event.getUserId() != null ? event.getUserId() : 0L)
                    .merchantId(event.getMerchantId())
                    .parkingLotId(event.getParkingLotId())
                    .depositAmount(event.getAdvanceDeposit() != null ? event.getAdvanceDeposit() : BigDecimal.ZERO)
                    .refundAmount(refundAmount)
                    .refundPercentage(percentage)
                    .currency("VND")
                    .policyCode(policyCode)
                    .status(STATUS_REQUIRES_RECONCILIATION)
                    .cancellationReason(event.getReason())
                    .verificationNote("Không tìm thấy giao dịch thanh toán SUCCESS khớp với yêu cầu hoàn tiền")
                    .dueAt(dueAt)
                    .build();
            RefundRequest saved = refundRequestRepository.save(unverified);
            outboxPublisherService.publishRefundStatusChanged(saved);
            return RefundRequestDto.fromEntity(saved);
        }

        Payment paidPayment = paidPaymentOpt.get();
        Long merchantId = event.getMerchantId() != null ? event.getMerchantId() : paidPayment.getMerchantId();
        Long driverId = event.getUserId() != null ? event.getUserId() : paidPayment.getDriverId();

        if (refundAmount.compareTo(paidPayment.getAmount()) > 0) {
            RefundRequest mismatch = RefundRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .bookingId(event.getBookingId())
                    .paymentId(paidPayment.getId())
                    .driverId(driverId)
                    .merchantId(merchantId)
                    .parkingLotId(
                            event.getParkingLotId() != null ? event.getParkingLotId() : paidPayment.getParkingLotId())
                    .depositAmount(paidPayment.getAmount())
                    .refundAmount(refundAmount)
                    .refundPercentage(percentage)
                    .currency("VND")
                    .policyCode(policyCode)
                    .status(STATUS_REQUIRES_RECONCILIATION)
                    .cancellationReason(event.getReason())
                    .verificationNote("Số tiền yêu cầu hoàn vượt quá số tiền đã thu; cần đối soát")
                    .dueAt(dueAt)
                    .build();
            RefundRequest saved = refundRequestRepository.save(mismatch);
            outboxPublisherService.publishRefundStatusChanged(saved);
            return RefundRequestDto.fromEntity(saved);
        }

        RefundRequest refundRequest = RefundRequest.builder()
                .idempotencyKey(idempotencyKey)
                .bookingId(event.getBookingId())
                .paymentId(paidPayment.getId())
                .driverId(driverId)
                .merchantId(merchantId)
                .parkingLotId(event.getParkingLotId() != null ? event.getParkingLotId() : paidPayment.getParkingLotId())
                .depositAmount(paidPayment.getAmount())
                .refundAmount(refundAmount)
                .refundPercentage(percentage)
                .currency("VND")
                .policyCode(policyCode)
                .status(STATUS_REQUESTED)
                .cancellationReason(event.getReason())
                .dueAt(dueAt)
                .build();

        RefundRequest saved = refundRequestRepository.save(refundRequest);

        merchantWalletService.recordRefundLiability(
                merchantId,
                event.getBookingId(),
                paidPayment.getId(),
                refundAmount,
                "REFUND_REQ:BOOKING:" + event.getBookingId(),
                "Ghi nhận nghĩa vụ hoàn tiền thủ công booking #" + event.getBookingId());

        outboxPublisherService.publishRefundStatusChanged(saved);
        log.info("Created manual refund request {} for booking {} with status={}",
                saved.getId(), saved.getBookingId(), saved.getStatus());
        return RefundRequestDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public RefundRequestDto submitTransferEvidence(Long merchantUserId, Long bookingId,
            SubmitRefundEvidenceRequest request) {
        RefundRequest refund = refundRequestRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy yêu cầu hoàn tiền cho booking này"));

        if (refund.getMerchantId() == null || !refund.getMerchantId().equals(merchantUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền cập nhật hoàn tiền của bãi xe khác");
        }

        if (STATUS_COMPLETED.equals(refund.getStatus())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu hoàn tiền này đã hoàn tất xác minh");
        }

        LocalDateTime now = LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE);
        String txRef = request != null && request.getTransactionReference() != null
                ? request.getTransactionReference().trim()
                : "";

        if (request == null || txRef.isEmpty() || request.isDisputed()) {
            refund.setStatus(STATUS_REQUIRES_RECONCILIATION);
            refund.setEvidenceSubmittedAt(now);
            refund.setMerchantNote(request != null ? request.getMerchantNote() : null);
            refund.setVerificationNote(
                    "Thiếu mã giao dịch chuyển khoản hoặc có tranh chấp từ phía nhà xe; cần đối soát");
            RefundRequest saved = refundRequestRepository.save(refund);
            outboxPublisherService.publishRefundStatusChanged(saved);
            log.warn(
                    "Refund request {} for booking {} moved to REQUIRES_RECONCILIATION due to missing/disputed evidence",
                    saved.getId(), bookingId);
            return RefundRequestDto.fromEntity(saved);
        }

        refund.setTransactionReference(txRef);
        refund.setEvidenceUrl(request.getEvidenceUrl());
        refund.setMerchantNote(request.getMerchantNote());
        refund.setEvidenceSubmittedAt(now);

        if (refund.getDueAt() != null && now.isAfter(refund.getDueAt())) {
            refund.setStatus(STATUS_REQUIRES_RECONCILIATION);
            refund.setVerificationNote("Bằng chứng hoàn tiền nộp sau thời hạn quy định; cần đối soát xác minh");
            log.warn("Late refund evidence submitted for booking {}; moved to REQUIRES_RECONCILIATION", bookingId);
        } else {
            refund.setStatus(STATUS_PROCESSING);
            log.info("Merchant submitted manual refund evidence for booking {}; status=PROCESSING", bookingId);
        }

        RefundRequest saved = refundRequestRepository.save(refund);
        outboxPublisherService.publishRefundStatusChanged(saved);
        return RefundRequestDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public RefundRequestDto verifyTransferEvidence(Long adminUserId, Long refundId,
            VerifyRefundEvidenceRequest request) {
        RefundRequest refund = refundRequestRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu hoàn tiền"));

        if (STATUS_COMPLETED.equals(refund.getStatus())) {
            return RefundRequestDto.fromEntity(refund);
        }

        LocalDateTime now = LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE);
        boolean hasValidEvidence = refund.getTransactionReference() != null
                && !refund.getTransactionReference().isBlank()
                && STATUS_PROCESSING.equals(refund.getStatus());

        if (request == null || !request.isApproved() || request.isDisputed() || !hasValidEvidence) {
            refund.setStatus(STATUS_REQUIRES_RECONCILIATION);
            refund.setVerifiedBy(adminUserId);
            refund.setVerifiedAt(now);
            refund.setVerificationNote(request != null && request.getVerificationNote() != null
                    ? request.getVerificationNote()
                    : "Bằng chứng chuyển khoản chưa đạt yêu cầu hoặc đang tranh chấp; cần đối soát");
            RefundRequest saved = refundRequestRepository.save(refund);
            outboxPublisherService.publishRefundStatusChanged(saved);
            log.warn("Refund {} moved to REQUIRES_RECONCILIATION by admin {}", refundId, adminUserId);
            return RefundRequestDto.fromEntity(saved);
        }

        refund.setStatus(STATUS_COMPLETED);
        refund.setVerifiedBy(adminUserId);
        refund.setVerifiedAt(now);
        refund.setVerificationNote(request.getVerificationNote() != null
                ? request.getVerificationNote()
                : "Đã xác minh bằng chứng chuyển khoản hoàn tiền hợp lệ");

        RefundRequest saved = refundRequestRepository.save(refund);

        if (refund.getPaymentId() != null) {
            paymentRepository.findByIdForUpdate(refund.getPaymentId()).ifPresent(payment -> {
                boolean isFullRefund = payment.getAmount() == null
                        || refund.getRefundAmount() == null
                        || refund.getRefundAmount().compareTo(payment.getAmount()) >= 0;
                if (isFullRefund) {
                    payment.setStatus(PaymentStatusEnum.REFUNDED);
                } else {
                    // For partial refund (e.g. 50%), retain SUCCESS status so remaining revenue is
                    // eligible for daily settlement
                    payment.setStatus(PaymentStatusEnum.SUCCESS);
                }
                paymentRepository.save(payment);
            });
        }

        merchantWalletService.settleCompletedRefund(
                refund.getMerchantId(),
                refund.getBookingId(),
                refund.getPaymentId(),
                refund.getRefundAmount(),
                "REFUND_COMPLETED:BOOKING:" + refund.getBookingId(),
                "Hoàn tất xác minh hoàn tiền thủ công cho booking #" + refund.getBookingId());

        outboxPublisherService.publishRefundStatusChanged(saved);
        log.info("Admin {} verified refund {} for booking {} as COMPLETED", adminUserId, refundId,
                refund.getBookingId());
        return RefundRequestDto.fromEntity(saved);
    }

    @Override
    @Transactional
    public List<RefundRequestDto> reconcileOverdueRefunds() {
        LocalDateTime now = LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE);
        List<RefundRequest> overdueList = refundRequestRepository.findByStatusAndDueAtBefore(STATUS_REQUESTED, now);
        List<RefundRequestDto> updated = new ArrayList<>();

        for (RefundRequest candidate : overdueList) {
            RefundRequest locked = refundRequestRepository.findByIdForUpdate(candidate.getId()).orElse(null);
            if (locked == null || !STATUS_REQUESTED.equals(locked.getStatus()) || !locked.getDueAt().isBefore(now)) {
                continue;
            }
            locked.setStatus(STATUS_REQUIRES_RECONCILIATION);
            locked.setVerificationNote(
                    "Quá hạn nộp bằng chứng hoàn tiền thủ công; chuyển sang trạng thái cần đối soát");
            RefundRequest saved = refundRequestRepository.save(locked);
            outboxPublisherService.publishRefundStatusChanged(saved);
            updated.add(RefundRequestDto.fromEntity(saved));
            log.warn("Overdue refund request {} for booking {} moved to REQUIRES_RECONCILIATION",
                    saved.getId(), saved.getBookingId());
        }
        return updated;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestDto> getMerchantRefunds(Long merchantUserId, Pageable pageable) {
        Page<RefundRequestDto> page = refundRequestRepository
                .findByMerchantIdOrderByCreatedAtDesc(merchantUserId, pageable)
                .map(RefundRequestDto::fromEntity);
        return new PageResponse<>(page);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestDto> getAllRefundsForAdmin(Pageable pageable) {
        Page<RefundRequestDto> page = refundRequestRepository.findAll(pageable)
                .map(RefundRequestDto::fromEntity);
        return new PageResponse<>(page);
    }
}
