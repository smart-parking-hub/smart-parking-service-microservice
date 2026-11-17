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

    @org.springframework.beans.factory.annotation.Autowired
    private com.smartparking.payment.application.service.DriverWalletLedger driverWallet;
    @org.springframework.beans.factory.annotation.Autowired
    private com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;
    @org.springframework.beans.factory.annotation.Autowired
    private com.smartparking.payment.domain.repository.LedgerJournalRepository journals;
    @org.springframework.beans.factory.annotation.Value("${app.driver-wallet-enabled:true}")
    private boolean walletEnabled = true;

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
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

        if (attemptGuard != null) attemptGuard.lock(event.getBookingId(), com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT);
        boolean walletRequested = "WALLET".equals(event.getRefundChannel());
        String channel = walletRequested ? "WALLET" : "BANK_MANUAL";
        String idempotencyKey = "refund:booking:" + event.getBookingId();
        Optional<RefundRequest> existing = refundRequestRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate cancellation refund event skipped for booking {}", event.getBookingId());
            return RefundRequestDto.fromEntity(existing.get());
        }

        var bookingPayments = paymentRepository.findByBookingId(event.getBookingId());
        var pendingWallet = bookingPayments.stream().filter(p -> p.getGateway() == com.smartparking.common.enums.PaymentGatewayEnum.WALLET && p.getStatus() == PaymentStatusEnum.PENDING).findFirst();
        if (pendingWallet.isPresent()) {
            var pending = pendingWallet.get();
            if (!java.util.Objects.equals(event.getUserId(), pending.getDriverId()) || !java.util.Objects.equals(event.getMerchantId(), pending.getMerchantId())
                    || refundAmount.compareTo(pending.getAmount()) > 0)
                throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu hoàn không khớp giao dịch ví đang xử lý");
            var deferred = refundRequestRepository.save(RefundRequest.builder().idempotencyKey(idempotencyKey)
                    .bookingId(pending.getBookingId()).paymentId(pending.getId()).driverId(pending.getDriverId()).merchantId(pending.getMerchantId())
                    .parkingLotId(pending.getParkingLotId()).depositAmount(pending.getAmount()).refundAmount(refundAmount)
                    .refundPercentage(event.getRefundPercentage()).policyCode(event.getRefundPolicy()).refundChannel("WALLET")
                    .status(STATUS_REQUESTED).dueAt(LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE).plusHours(24))
                    .verificationNote("Chờ xác nhận khoản cọc; tiền hoàn chưa được cộng vào ví").build());
            outboxPublisherService.publishRefundStatusChanged(deferred);
            return RefundRequestDto.fromEntity(deferred);
        }
        var candidates = bookingPayments.stream().filter(p -> p.getStatus() == PaymentStatusEnum.SUCCESS)
                .filter(p -> p.getPaymentType() == com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT || (!walletRequested && p.getPaymentType() == null))
                .filter(p -> event.getUserId() == null || java.util.Objects.equals(event.getUserId(), p.getDriverId()))
                .filter(p -> event.getMerchantId() == null || java.util.Objects.equals(event.getMerchantId(), p.getMerchantId())).toList();
        Optional<Payment> paidPaymentOpt = candidates.size() == 1 ? Optional.of(candidates.getFirst()) : Optional.empty();

        LocalDateTime now = LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE);
        LocalDateTime dueAt = now.plusHours(24);
        String policyCode = event.getRefundPolicy() != null ? event.getRefundPolicy() : "FULL_100_AT_OR_BEFORE_2H";
        int percentage = event.getRefundPercentage() != null ? event.getRefundPercentage() : 100;

        if (paidPaymentOpt.isEmpty()) {
            RefundRequest unverified = RefundRequest.builder()
                    .idempotencyKey(idempotencyKey)
                    .refundChannel(channel)
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
                    .refundChannel(channel)
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
                    .refundChannel(channel)
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

        boolean toWallet = walletRequested || paidPayment.isWalletRefund();
        if (toWallet) refundRequest.setRefundChannel("WALLET");
        if (toWallet && (!walletEnabled || !canFundWalletRefund(paidPayment, refundAmount))) {
            refundRequest.setStatus(STATUS_REQUIRES_RECONCILIATION);
            refundRequest.setVerificationNote("Hoàn ví đang tạm dừng hoặc số dư chủ bãi cần được đối soát");
            var blocked = refundRequestRepository.save(refundRequest);
            outboxPublisherService.publishRefundStatusChanged(blocked);
            return RefundRequestDto.fromEntity(blocked);
        }
        RefundRequest saved = refundRequestRepository.save(refundRequest);

        merchantWalletService.recordRefundLiability(
                merchantId,
                event.getBookingId(),
                paidPayment.getId(),
                refundAmount,
                "REFUND_REQ:BOOKING:" + event.getBookingId(),
                "Ghi nhận nghĩa vụ hoàn tiền thủ công booking #" + event.getBookingId());

        if (toWallet) completeWalletRefund(saved, paidPayment, now);
        outboxPublisherService.publishRefundStatusChanged(saved);
        log.info("Created refund request {} for booking {} with status={}",
                saved.getId(), saved.getBookingId(), saved.getStatus());
        return RefundRequestDto.fromEntity(saved);
    }

    private void completeWalletRefund(RefundRequest saved, Payment payment, LocalDateTime now) {
        driverWallet.post(saved.getDriverId(), saved.getBookingId(), payment.getId(), "WALLET:REFUND:" + payment.getId(), "REFUND", saved.getRefundAmount(),
                com.smartparking.payment.domain.model.LedgerAccountCode.REFUND_PAYABLE,
                com.smartparking.payment.domain.model.LedgerAccountCode.DRIVER_WALLET_PAYABLE, saved.getRefundAmount(), BigDecimal.ZERO);
        saved.setStatus(STATUS_COMPLETED);
        saved.setTransactionReference("WALLET-REFUND-" + payment.getId());
        saved.setVerifiedAt(now);
        saved.setVerificationNote("Đã ghi có tiền hoàn vào ví tài xế");
        refundRequestRepository.save(saved);
        if (saved.getRefundAmount().compareTo(payment.getAmount()) == 0) payment.setStatus(PaymentStatusEnum.REFUNDED);
        paymentRepository.save(payment);
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public RefundRequestDto retryWalletRefund(Long refundId, Long adminId) {
        var snapshot = refundRequestRepository.findById(refundId).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy yêu cầu hoàn"));
        attemptGuard.lock(snapshot.getBookingId(), com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT);
        var refund = refundRequestRepository.findByIdForUpdate(refundId).orElseThrow();
        if (!"WALLET".equals(refund.getRefundChannel())) throw new AppException(ErrorCode.BAD_REQUEST, "Yêu cầu này thuộc kênh ngân hàng");
        if (STATUS_COMPLETED.equals(refund.getStatus())) return RefundRequestDto.fromEntity(refund);
        if (refund.getPaymentId() == null) throw new AppException(ErrorCode.BAD_REQUEST, "Chưa xác minh được giao dịch cọc gốc");
        var payment = paymentRepository.findByIdForUpdate(refund.getPaymentId()).orElseThrow();
        if (payment.getStatus() != PaymentStatusEnum.SUCCESS || payment.getPaymentType() != com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT
                || !java.util.Objects.equals(payment.getDriverId(), refund.getDriverId()) || !java.util.Objects.equals(payment.getMerchantId(), refund.getMerchantId())
                || refund.getRefundAmount().signum() <= 0 || refund.getRefundAmount().compareTo(payment.getAmount()) > 0)
            throw new AppException(ErrorCode.BAD_REQUEST, "Dữ liệu hoàn không khớp giao dịch cọc");
        if (!walletEnabled || !canFundWalletRefund(payment, refund.getRefundAmount()))
            throw new AppException(ErrorCode.CONFLICT, "Chưa đủ điều kiện hoàn ví; cần đối soát số dư chủ bãi");
        merchantWalletService.recordRefundLiability(refund.getMerchantId(), refund.getBookingId(), payment.getId(), refund.getRefundAmount(),
                "REFUND_REQ:BOOKING:" + refund.getBookingId(), "Đối soát hoàn ví");
        refund.setVerifiedBy(adminId);
        completeWalletRefund(refund, payment, LocalDateTime.now(DailySettlementServiceImpl.BUSINESS_ZONE));
        outboxPublisherService.publishRefundStatusChanged(refund);
        return RefundRequestDto.fromEntity(refund);
    }

    private boolean canFundWalletRefund(Payment payment, BigDecimal refund) {
        if (journals == null) return false;
        BigDecimal escrow = journals.findByPaymentId(payment.getId()).stream().flatMap(j -> j.getPostings().stream())
                .filter(p -> p.getAccountCode() == com.smartparking.payment.domain.model.LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW)
                .map(p -> p.getDirection() == com.smartparking.payment.domain.model.PostingDirection.CREDIT ? p.getAmount() : p.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO);
        BigDecimal reversalAmount = refund.subtract(escrow.min(refund));
        if (reversalAmount.signum() == 0) return true;
        var settlement = journals.findByJournalKey("SETTLE:PAYMENT:" + payment.getId());
        if (settlement.isEmpty()) return false;
        var original = settlement.get();
        var fee = original.getPostings().stream().filter(p -> p.getAccountCode() == com.smartparking.payment.domain.model.LedgerAccountCode.PLATFORM_FEE_REVENUE)
                .map(com.smartparking.payment.domain.model.LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var gross = original.getTotalDebit();
        if (gross == null || gross.compareTo(reversalAmount) < 0) return false;
        var retainedFee = gross.subtract(reversalAmount).multiply(original.getFeePercent()).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        var merchantPart = reversalAmount.subtract(fee.subtract(retainedFee));
        return merchantWalletService.getWalletBalance(payment.getMerchantId()).compareTo(merchantPart) >= 0;
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

        if ("WALLET".equals(refund.getRefundChannel()))
            throw new AppException(ErrorCode.BAD_REQUEST, "Khoản hoàn ví không được xử lý chuyển khoản ngân hàng");
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

        if ("WALLET".equals(refund.getRefundChannel()))
            throw new AppException(ErrorCode.BAD_REQUEST, "Khoản hoàn ví không được xử lý chuyển khoản ngân hàng");
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
            if (locked == null || "WALLET".equals(locked.getRefundChannel()) || !STATUS_REQUESTED.equals(locked.getStatus()) || !locked.getDueAt().isBefore(now)) {
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
