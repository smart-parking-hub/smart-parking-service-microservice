package com.smartparking.payment.application.service.impl;

import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.DailySettlementService;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.LedgerJournal;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.model.SettlementBatch;
import com.smartparking.payment.domain.repository.LedgerJournalRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import com.smartparking.payment.domain.repository.SettlementBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailySettlementServiceImpl implements DailySettlementService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final PaymentRepository paymentRepository;
    private final SettlementBatchRepository settlementBatchRepository;
    private final LedgerJournalRepository ledgerJournalRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final MerchantWalletService merchantWalletService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;

    @Override
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public List<SettlementBatch> runDailySettlement(LocalDate businessDate) {
        LocalDate targetDate = businessDate != null
                ? businessDate
                : LocalDate.now(BUSINESS_ZONE).minusDays(1);

        LocalDateTime windowStart = targetDate.atStartOfDay();
        LocalDateTime windowEnd = targetDate.plusDays(1).atStartOfDay();

        List<Payment> payments = paymentRepository.findSuccessfulPaymentsInWindow(windowStart, windowEnd);
        if (payments == null || payments.isEmpty()) {
            log.info("No successful payments found for daily settlement on {}", targetDate);
            return settlementBatchRepository.findByBusinessDate(targetDate);
        }

        // Acquire groups in stable order before any merchant wallet lock, fencing refunds/capture.
        if (attemptGuard != null) payments.stream().sorted(java.util.Comparator.comparing(Payment::getBookingId).thenComparing(Payment::getId))
                .forEach(p -> attemptGuard.lock(p.getBookingId(), p.getPaymentType()));
        Map<Long, List<Payment>> byMerchant = new LinkedHashMap<>();
        for (Payment p : payments) {
            if (p.getMerchantId() != null) {
                byMerchant.computeIfAbsent(p.getMerchantId(), k -> new ArrayList<>()).add(p);
            }
        }

        List<SettlementBatch> results = new ArrayList<>();
        for (Map.Entry<Long, List<Payment>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey();
            Optional<SettlementBatch> existingBatch =
                    settlementBatchRepository.findByBusinessDateAndMerchantId(targetDate, merchantId);
            if (existingBatch.isPresent() && "COMPLETED".equals(existingBatch.get().getStatus())) {
                log.info("Daily settlement batch already completed for date={} merchant={}", targetDate, merchantId);
                results.add(existingBatch.get());
                continue;
            }

            BigDecimal totalGross = BigDecimal.ZERO;
            BigDecimal totalFee = BigDecimal.ZERO;
            BigDecimal totalNet = BigDecimal.ZERO;
            BigDecimal feePercent = new BigDecimal("10.00");
            int settledCount = 0;
            Long lastJournalId = null;

            for (Payment payment : entry.getValue()) {
                BigDecimal settleableGross = computeSettleableGross(payment);
                if (settleableGross.signum() <= 0) {
                    continue;
                }

                String journalKey = "SETTLE:PAYMENT:" + payment.getId();
                Optional<LedgerJournal> existingJournal = ledgerJournalRepository.findByJournalKey(journalKey);
                SettlementSplit split;
                if (existingJournal.isPresent()) {
                    var journal = existingJournal.get();
                    BigDecimal recordedNet = journal.getPostings().stream()
                            .filter(p -> p.getAccountCode() == com.smartparking.payment.domain.model.LedgerAccountCode.MERCHANT_PAYABLE && p.getDirection() == com.smartparking.payment.domain.model.PostingDirection.CREDIT)
                            .map(com.smartparking.payment.domain.model.LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal recordedFee = journal.getPostings().stream()
                            .filter(p -> p.getAccountCode() == com.smartparking.payment.domain.model.LedgerAccountCode.PLATFORM_FEE_REVENUE && p.getDirection() == com.smartparking.payment.domain.model.PostingDirection.CREDIT)
                            .map(com.smartparking.payment.domain.model.LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    split = SettlementSplit.builder().grossAmount(recordedNet.add(recordedFee)).feePercent(journal.getFeePercent()).feeAmount(recordedFee).netAmount(recordedNet).build();
                    lastJournalId = existingJournal.get().getId();
                } else {
                    split = merchantWalletService.creditSettlement(
                            merchantId,
                            payment.getBookingId(),
                            payment,
                            settleableGross,
                            "Quyết toán doanh thu ngày " + targetDate + " cho giao dịch #" + payment.getId()
                    );
                    lastJournalId = ledgerJournalRepository.findByJournalKey(journalKey)
                            .map(LedgerJournal::getId)
                            .orElse(null);
                }

                totalGross = totalGross.add(split.getGrossAmount());
                totalFee = totalFee.add(split.getFeeAmount());
                totalNet = totalNet.add(split.getNetAmount());
                feePercent = split.getFeePercent();
                settledCount++;
            }

            String batchKey = "SETTLEMENT:" + targetDate + ":MERCHANT:" + merchantId;
            SettlementBatch batch = existingBatch.orElseGet(() -> SettlementBatch.builder()
                    .batchKey(batchKey)
                    .businessDate(targetDate)
                    .merchantId(merchantId)
                    .build());

            batch.setGrossAmount(totalGross);
            batch.setFeePercent(feePercent);
            batch.setFeeAmount(totalFee);
            batch.setNetAmount(totalNet);
            batch.setPaymentCount(settledCount);
            batch.setStatus("COMPLETED");
            batch.setJournalId(lastJournalId);

            try {
                SettlementBatch savedBatch = settlementBatchRepository.saveAndFlush(batch);
                results.add(savedBatch);
                log.info("Completed daily settlement batch {} for merchant {}: gross={} fee={} net={} count={}",
                        batchKey, merchantId, totalGross, totalFee, totalNet, settledCount);
            } catch (DataIntegrityViolationException duplicateEx) {
                log.info("Concurrent daily settlement batch detected for date={} merchant={}", targetDate, merchantId);
                settlementBatchRepository.findByBusinessDateAndMerchantId(targetDate, merchantId)
                        .ifPresent(results::add);
            }
        }

        return results;
    }

    private BigDecimal computeSettleableGross(Payment payment) {
        if (payment == null || payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = payment.getAmount();
        if (payment.getBookingId() != null && refundRequestRepository != null) {
            Optional<RefundRequest> refundOpt = refundRequestRepository.findByBookingId(payment.getBookingId());
            if (refundOpt.isPresent() && refundOpt.get().getPaymentId() == null
                    && payment.getPaymentType() == com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT
                    && !"FAILED".equals(refundOpt.get().getStatus()) && !"NONE".equals(refundOpt.get().getStatus())
                    && refundOpt.get().getRefundAmount() != null && refundOpt.get().getRefundAmount().signum() > 0) {
                // Unverified refund ownership must keep the deposit in escrow until reconciliation.
                return BigDecimal.ZERO;
            }
            if (refundOpt.isPresent() && java.util.Objects.equals(refundOpt.get().getPaymentId(), payment.getId())) {
                RefundRequest refund = refundOpt.get();
                if (!"FAILED".equals(refund.getStatus()) && !"NONE".equals(refund.getStatus())
                        && refund.getRefundAmount() != null && refund.getRefundAmount().signum() > 0) {
                    gross = gross.subtract(refund.getRefundAmount()).max(BigDecimal.ZERO);
                }
            }
        }
        return gross;
    }
}

