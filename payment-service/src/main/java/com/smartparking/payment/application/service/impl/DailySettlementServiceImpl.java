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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DailySettlementServiceImpl implements DailySettlementService {

    private record BatchKey(Long merchantId, LocalDate businessDate) {}

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

        List<Payment> payments = new ArrayList<>(paymentRepository.findSuccessfulPaymentsInWindow(windowStart, windowEnd));
        payments.addAll(paymentRepository.findUnsettledWalletDepositsBefore(windowStart));
        if (payments.isEmpty()) {
            log.info("No successful payments found for daily settlement on {}", targetDate);
            return settlementBatchRepository.findByBusinessDate(targetDate);
        }

        // Lock all affected dates before payment groups and merchant wallets. Backlog belongs to
        // its original date, so a later rerun cannot count one journal in two daily batches.
        if (attemptGuard != null) {
            payments.stream().map(p -> businessDate(p, targetDate)).distinct().sorted()
                    .forEach(attemptGuard::lockSettlementDate);
            payments.stream().sorted(java.util.Comparator.comparing(Payment::getBookingId).thenComparing(Payment::getId))
                    .forEach(p -> attemptGuard.lock(p.getBookingId(), p.getPaymentType()));
        }
        Map<BatchKey, List<Payment>> byMerchant = new TreeMap<>(java.util.Comparator
                .comparing(BatchKey::merchantId).thenComparing(BatchKey::businessDate));
        for (Payment p : payments) {
            if (p.getMerchantId() != null) {
                var key = new BatchKey(p.getMerchantId(), businessDate(p, targetDate));
                byMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            }
        }

        List<SettlementBatch> results = new ArrayList<>();
        for (Map.Entry<BatchKey, List<Payment>> entry : byMerchant.entrySet()) {
            Long merchantId = entry.getKey().merchantId();
            LocalDate settlementDate = entry.getKey().businessDate();
            Optional<SettlementBatch> existingBatch =
                    settlementBatchRepository.findByBusinessDateAndMerchantId(settlementDate, merchantId);
            boolean completedBatch = existingBatch.isPresent() && "COMPLETED".equals(existingBatch.get().getStatus());
            // A completed batch can receive a delta for a newly captured payment. Existing journals
            // remain immutable and must not be added to the batch twice.
            BigDecimal totalGross = completedBatch ? existingBatch.get().getGrossAmount() : BigDecimal.ZERO;
            BigDecimal totalFee = completedBatch ? existingBatch.get().getFeeAmount() : BigDecimal.ZERO;
            BigDecimal totalNet = completedBatch ? existingBatch.get().getNetAmount() : BigDecimal.ZERO;
            BigDecimal feePercent = new BigDecimal("10.00");
            int settledCount = completedBatch ? existingBatch.get().getPaymentCount() : 0;
            Long lastJournalId = completedBatch ? existingBatch.get().getJournalId() : null;
            int addedCount = 0;

            for (Payment payment : entry.getValue()) {
                BigDecimal settleableGross = computeSettleableGross(payment);
                if (settleableGross.signum() <= 0) {
                    continue;
                }

                String journalKey = "SETTLE:PAYMENT:" + payment.getId();
                Optional<LedgerJournal> existingJournal = ledgerJournalRepository.findByJournalKey(journalKey);
                SettlementSplit split;
                if (existingJournal.isPresent()) {
                    // A concurrent run may have settled this payment while we waited for locks.
                    if (completedBatch) {
                        continue;
                    }
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
                            "Quyết toán doanh thu ngày " + settlementDate + " cho giao dịch #" + payment.getId()
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
                addedCount++;
            }

            if (completedBatch && addedCount == 0) {
                results.add(existingBatch.get());
                continue;
            }

            String batchKey = "SETTLEMENT:" + settlementDate + ":MERCHANT:" + merchantId;
            SettlementBatch batch = existingBatch.orElseGet(() -> SettlementBatch.builder()
                    .batchKey(batchKey)
                    .businessDate(settlementDate)
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
                log.info("Concurrent daily settlement batch detected for date={} merchant={}", settlementDate, merchantId);
                settlementBatchRepository.findByBusinessDateAndMerchantId(settlementDate, merchantId)
                        .ifPresent(results::add);
            }
        }

        return results;
    }

    private LocalDate businessDate(Payment payment, LocalDate fallback) {
        return payment.getCreatedAt() == null ? fallback : payment.getCreatedAt().toLocalDate();
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

