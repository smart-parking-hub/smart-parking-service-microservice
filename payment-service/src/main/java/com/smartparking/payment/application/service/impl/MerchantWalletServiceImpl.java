package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.dto.RevenueSeriesPointDto;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.LedgerAccountCode;
import com.smartparking.payment.domain.model.LedgerJournal;
import com.smartparking.payment.domain.model.LedgerPosting;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.PostingDirection;
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.LedgerJournalRepository;
import com.smartparking.payment.domain.repository.LedgerPostingRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantWalletServiceImpl implements MerchantWalletService {

    @Value("${app.platform.fee-percent:10.0}")
    private BigDecimal platformFeePercent = new BigDecimal("10.0");

    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository ledgerRepository;
    private final PaymentRepository paymentRepository;

    @Autowired(required = false)
    private LedgerJournalRepository ledgerJournalRepository;

    @Autowired(required = false)
    private LedgerPostingRepository ledgerPostingRepository;

    @Override
    public SettlementSplit split(BigDecimal grossAmount) {
        BigDecimal effectiveFeePercent = platformFeePercent != null ? platformFeePercent : new BigDecimal("10.0");
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return SettlementSplit.builder()
                    .grossAmount(BigDecimal.ZERO)
                    .feePercent(effectiveFeePercent)
                    .feeAmount(BigDecimal.ZERO)
                    .netAmount(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal feeRate = effectiveFeePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal feeAmount = grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.setScale(2, RoundingMode.HALF_UP).subtract(feeAmount);

        return SettlementSplit.builder()
                .grossAmount(grossAmount)
                .feePercent(effectiveFeePercent)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .build();
    }

    @Override
    @Transactional
    public void recordPaymentEscrow(Payment payment, String note) {
        if (payment == null || payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            return;
        }
        String journalKey = "ESCROW:PAYMENT:" + (payment.getId() != null ? payment.getId() : UUID.randomUUID());
        if (ledgerJournalRepository != null && ledgerJournalRepository.existsByJournalKey(journalKey)) {
            return;
        }

        BigDecimal gross = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        LedgerJournal journal = LedgerJournal.builder()
                .journalKey(journalKey)
                .journalType("PAYMENT_ESCROW")
                .merchantId(payment.getMerchantId())
                .bookingId(payment.getBookingId())
                .paymentId(payment.getId())
                .currency("VND")
                .feePercent(BigDecimal.ZERO)
                .description(note != null ? note : "Ghi nhận tạm giữ thanh toán #" + payment.getId())
                .build();
        journal.addPosting(LedgerAccountCode.GATEWAY_CLEARING, payment.getMerchantId(), PostingDirection.DEBIT, gross);
        journal.addPosting(LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW, payment.getMerchantId(), PostingDirection.CREDIT, gross);
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }
    }

    @Override
    @Transactional
    public SettlementSplit creditSettlement(Long merchantId, Long bookingId, Payment payment, BigDecimal grossAmount, String note) {
        if (grossAmount == null || grossAmount.signum() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền quyết toán phải lớn hơn 0");
        }
        SettlementSplit split = split(grossAmount);
        Long paymentId = payment != null ? payment.getId() : null;
        String journalKey = buildSettlementJournalKey(merchantId, bookingId, paymentId);

        if (ledgerJournalRepository != null && paymentId != null && ledgerJournalRepository.existsByJournalKey(journalKey)) {
            log.info("Skipping duplicate settlement for journalKey={}", journalKey);
            return split;
        }

        Wallet wallet = getOrCreateWalletForUpdate(merchantId);

        boolean hasEscrow = paymentId != null
                && ledgerJournalRepository != null
                && ledgerJournalRepository.existsByJournalKey("ESCROW:PAYMENT:" + paymentId);
        LedgerAccountCode debitAccount = hasEscrow
                ? LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW
                : LedgerAccountCode.GATEWAY_CLEARING;

        BigDecimal grossScaled = split.getGrossAmount().setScale(2, RoundingMode.HALF_UP);
        LedgerJournal journal = LedgerJournal.builder()
                .journalKey(journalKey)
                .journalType("SETTLEMENT_CREDIT")
                .merchantId(merchantId)
                .bookingId(bookingId)
                .paymentId(paymentId)
                .currency("VND")
                .feePercent(split.getFeePercent())
                .description(note)
                .build();
        journal.addPosting(debitAccount, merchantId, PostingDirection.DEBIT, grossScaled);
        journal.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, merchantId, PostingDirection.CREDIT, split.getNetAmount());
        if (split.getFeeAmount().signum() > 0) {
            journal.addPosting(LedgerAccountCode.PLATFORM_FEE_REVENUE, merchantId, PostingDirection.CREDIT, split.getFeeAmount());
        }
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }

        WalletLedgerEntry entry = WalletLedgerEntry.builder()
                .merchantId(merchantId)
                .bookingId(bookingId)
                .payment(payment)
                .entryType(WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT)
                .grossAmount(split.getGrossAmount())
                .feePercent(split.getFeePercent())
                .feeAmount(split.getFeeAmount())
                .netAmount(split.getNetAmount())
                .note(note)
                .build();

        ledgerRepository.save(entry);

        wallet.applyJournal(journal);
        walletRepository.save(wallet);

        log.info("Credited settlement to merchant {} for booking {}: net={} fee={}",
                merchantId, bookingId, split.getNetAmount(), split.getFeeAmount());
        return split;
    }

    @Override
    @Transactional
    public void holdWithdrawal(Long merchantId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền rút phải lớn hơn 0!");
        }
        Wallet wallet = getOrCreateWalletForUpdate(merchantId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số dư khả dụng không đủ để rút tiền!");
        }

        LedgerJournal journal = LedgerJournal.builder()
                .journalKey("WITHDRAWAL_HOLD:MERCHANT:" + merchantId + ":" + UUID.randomUUID())
                .journalType("WITHDRAWAL_HOLD")
                .merchantId(merchantId)
                .currency("VND")
                .feePercent(BigDecimal.ZERO)
                .description(note)
                .build();
        journal.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, merchantId, PostingDirection.DEBIT, amount);
        journal.addPosting(LedgerAccountCode.MERCHANT_WITHDRAWAL_HOLD, merchantId, PostingDirection.CREDIT, amount);
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }

        WalletLedgerEntry entry = WalletLedgerEntry.builder()
                .merchantId(merchantId)
                .entryType(WalletLedgerEntryTypeEnum.WITHDRAWAL_HOLD)
                .grossAmount(amount)
                .feePercent(BigDecimal.ZERO)
                .feeAmount(BigDecimal.ZERO)
                .netAmount(amount.negate())
                .note(note)
                .build();

        ledgerRepository.save(entry);

        wallet.applyJournal(journal);
        walletRepository.save(wallet);

        log.info("Held withdrawal amount {} from merchant {}", amount, merchantId);
    }

    @Override
    @Transactional
    public void refundWithdrawal(Long merchantId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số tiền hoàn rút phải lớn hơn 0!");
        }
        Wallet wallet = getOrCreateWalletForUpdate(merchantId);

        LedgerJournal journal = LedgerJournal.builder()
                .journalKey("WITHDRAWAL_REFUND:MERCHANT:" + merchantId + ":" + UUID.randomUUID())
                .journalType("WITHDRAWAL_REFUND")
                .merchantId(merchantId)
                .currency("VND")
                .feePercent(BigDecimal.ZERO)
                .description(note)
                .build();
        journal.addPosting(LedgerAccountCode.MERCHANT_WITHDRAWAL_HOLD, merchantId, PostingDirection.DEBIT, amount);
        journal.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, merchantId, PostingDirection.CREDIT, amount);
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }

        WalletLedgerEntry entry = WalletLedgerEntry.builder()
                .merchantId(merchantId)
                .entryType(WalletLedgerEntryTypeEnum.WITHDRAWAL_REFUND)
                .grossAmount(amount)
                .feePercent(BigDecimal.ZERO)
                .feeAmount(BigDecimal.ZERO)
                .netAmount(amount)
                .note(note)
                .build();

        ledgerRepository.save(entry);

        wallet.applyJournal(journal);
        walletRepository.save(wallet);

        log.info("Refunded withdrawal amount {} to merchant {}", amount, merchantId);
    }

    @Override
    @Transactional
    public void settleApprovedWithdrawal(Long merchantId, Long withdrawalId, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        String journalKey = "WITHDRAWAL_PAID:" + (withdrawalId != null ? withdrawalId : UUID.randomUUID());
        if (ledgerJournalRepository != null && ledgerJournalRepository.existsByJournalKey(journalKey)) {
            return;
        }
        LedgerJournal journal = LedgerJournal.builder()
                .journalKey(journalKey)
                .journalType("WITHDRAWAL_PAID")
                .merchantId(merchantId)
                .currency("VND")
                .feePercent(BigDecimal.ZERO)
                .description(note)
                .build();
        journal.addPosting(LedgerAccountCode.MERCHANT_WITHDRAWAL_HOLD, merchantId, PostingDirection.DEBIT, amount);
        journal.addPosting(LedgerAccountCode.GATEWAY_CLEARING, merchantId, PostingDirection.CREDIT, amount);
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }
    }

    @Override
    @Transactional
    public void recordRefundLiability(Long merchantId, Long bookingId, Long paymentId, BigDecimal refundAmount, String idempotencyKey, String note) {
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        String journalKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "REFUND_REQ:BOOKING:" + bookingId;
        if (ledgerJournalRepository != null && ledgerJournalRepository.existsByJournalKey(journalKey)) {
            return;
        }

        BigDecimal scaledRefund = refundAmount.setScale(2, RoundingMode.HALF_UP);
        boolean alreadySettledToMerchant = paymentId != null
                && ledgerJournalRepository != null
                && ledgerJournalRepository.existsByJournalKey("SETTLE:PAYMENT:" + paymentId);

        LedgerJournal journal = LedgerJournal.builder()
                .journalKey(journalKey)
                .journalType("REFUND_REQUESTED")
                .merchantId(merchantId)
                .bookingId(bookingId)
                .paymentId(paymentId)
                .currency("VND")
                .description(note)
                .build();

        if (alreadySettledToMerchant && merchantId != null) {
            SettlementSplit reversalSplit = split(scaledRefund);
            journal.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, merchantId, PostingDirection.DEBIT, reversalSplit.getNetAmount());
            if (reversalSplit.getFeeAmount().signum() > 0) {
                journal.addPosting(LedgerAccountCode.PLATFORM_FEE_REVENUE, merchantId, PostingDirection.DEBIT, reversalSplit.getFeeAmount());
            }
            journal.addPosting(LedgerAccountCode.REFUND_PAYABLE, merchantId, PostingDirection.CREDIT, scaledRefund);
            journal.validateAndSeal();

            if (ledgerJournalRepository != null) {
                ledgerJournalRepository.save(journal);
            }

            Wallet wallet = getOrCreateWalletForUpdate(merchantId);
            wallet.applyJournal(journal);
            walletRepository.save(wallet);

            WalletLedgerEntry adjustment = WalletLedgerEntry.builder()
                    .merchantId(merchantId)
                    .bookingId(bookingId)
                    .entryType(WalletLedgerEntryTypeEnum.ADJUSTMENT)
                    .grossAmount(scaledRefund)
                    .feePercent(reversalSplit.getFeePercent())
                    .feeAmount(reversalSplit.getFeeAmount().negate())
                    .netAmount(reversalSplit.getNetAmount().negate())
                    .note(note)
                    .build();
            ledgerRepository.save(adjustment);
        } else {
            journal.addPosting(LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW, merchantId, PostingDirection.DEBIT, scaledRefund);
            journal.addPosting(LedgerAccountCode.REFUND_PAYABLE, merchantId, PostingDirection.CREDIT, scaledRefund);
            journal.validateAndSeal();

            if (ledgerJournalRepository != null) {
                ledgerJournalRepository.save(journal);
            }
        }
    }

    @Override
    @Transactional
    public void settleCompletedRefund(Long merchantId, Long bookingId, Long paymentId, BigDecimal refundAmount, String idempotencyKey, String note) {
        if (refundAmount == null || refundAmount.signum() <= 0) {
            return;
        }
        String journalKey = idempotencyKey != null && !idempotencyKey.isBlank()
                ? idempotencyKey
                : "REFUND_COMPLETED:BOOKING:" + bookingId;
        if (ledgerJournalRepository != null && ledgerJournalRepository.existsByJournalKey(journalKey)) {
            return;
        }
        BigDecimal scaled = refundAmount.setScale(2, RoundingMode.HALF_UP);
        LedgerJournal journal = LedgerJournal.builder()
                .journalKey(journalKey)
                .journalType("REFUND_COMPLETED")
                .merchantId(merchantId)
                .bookingId(bookingId)
                .paymentId(paymentId)
                .currency("VND")
                .feePercent(BigDecimal.ZERO)
                .description(note)
                .build();
        journal.addPosting(LedgerAccountCode.REFUND_PAYABLE, merchantId, PostingDirection.DEBIT, scaled);
        journal.addPosting(LedgerAccountCode.GATEWAY_CLEARING, merchantId, PostingDirection.CREDIT, scaled);
        journal.validateAndSeal();

        if (ledgerJournalRepository != null) {
            ledgerJournalRepository.save(journal);
        }
    }

    @Override
    @Transactional
    public LedgerJournal reverseJournal(Long journalId, String reason) {
        if (ledgerJournalRepository == null) {
            throw new IllegalStateException("LedgerJournalRepository is not available");
        }
        LedgerJournal original = ledgerJournalRepository.findById(journalId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Không tìm thấy bút toán gốc để đảo"));
        String reversalKey = "REV:" + original.getJournalKey();
        var existing = ledgerJournalRepository.findByJournalKey(reversalKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        LedgerJournal reversal = LedgerJournal.builder()
                .journalKey(reversalKey)
                .journalType("REVERSAL")
                .merchantId(original.getMerchantId())
                .bookingId(original.getBookingId())
                .paymentId(original.getPaymentId())
                .currency(original.getCurrency())
                .feePercent(original.getFeePercent())
                .reversalOfJournalId(original.getId())
                .description(reason != null ? reason : "Đảo bút toán #" + original.getId())
                .build();

        for (LedgerPosting p : original.getPostings()) {
            PostingDirection inverse = p.getDirection() == PostingDirection.DEBIT
                    ? PostingDirection.CREDIT
                    : PostingDirection.DEBIT;
            reversal.addPosting(p.getAccountCode(), p.getMerchantId(), inverse, p.getAmount());
        }
        reversal.validateAndSeal();
        LedgerJournal saved = ledgerJournalRepository.save(reversal);

        if (original.getMerchantId() != null) {
            BigDecimal merchantDelta = saved.netMerchantPayableDelta(original.getMerchantId());
            if (merchantDelta.signum() != 0) {
                Wallet wallet = getOrCreateWalletForUpdate(original.getMerchantId());
                wallet.applyJournal(saved);
                walletRepository.save(wallet);
            }
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getWalletBalance(Long merchantId) {
        return walletRepository.findByMerchantId(merchantId)
                .map(Wallet::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getLedgerBackedBalance(Long merchantId) {
        if (ledgerPostingRepository == null) {
            return getWalletBalance(merchantId);
        }
        BigDecimal sum = ledgerPostingRepository.sumCreditMinusDebitByAccountAndMerchant(
                LedgerAccountCode.MERCHANT_PAYABLE, merchantId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWalletReconciled(Long merchantId) {
        BigDecimal walletBalance = getWalletBalance(merchantId);
        BigDecimal ledgerBalance = getLedgerBackedBalance(merchantId);
        return walletBalance.compareTo(ledgerBalance) == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId) {
        return getRevenueSummary(merchantId, null, null, null, "DAY");
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId, Long parkingLotId, String startDate, String endDate, String granularity) {
        BigDecimal totalGross;
        BigDecimal totalFee;
        BigDecimal totalNet;

        if (parkingLotId != null) {
            BigDecimal lotGross = paymentRepository.sumTotalRevenueByMerchantIdAndParkingLotId(merchantId, parkingLotId);
            totalGross = lotGross != null ? lotGross : BigDecimal.ZERO;
            BigDecimal feePercent = platformFeePercent != null ? platformFeePercent : BigDecimal.valueOf(10.0);
            totalNet = totalGross.multiply(BigDecimal.valueOf(100).subtract(feePercent))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            totalFee = totalGross.subtract(totalNet);
        } else {
            BigDecimal rawGross = paymentRepository.sumTotalRevenueByMerchantId(merchantId);
            BigDecimal rawFee = ledgerRepository.sumPlatformFeeByMerchantId(merchantId);
            BigDecimal rawNet = ledgerRepository.sumNetAmountByMerchantId(merchantId);
            totalGross = rawGross != null ? rawGross : BigDecimal.ZERO;
            totalFee = rawFee != null ? rawFee : BigDecimal.ZERO;
            totalNet = rawNet != null ? rawNet : BigDecimal.ZERO;
        }

        BigDecimal currentBalance = getWalletBalance(merchantId);
        BigDecimal safeGross = totalGross;
        BigDecimal safeNet = totalNet;

        List<RevenueSeriesPointDto> series = calculateRevenueSeries(merchantId, parkingLotId, startDate, endDate);

        long completedBookings = series.stream()
                .mapToLong(RevenueSeriesPointDto::getBookingCount)
                .sum();

        return MerchantRevenueResponse.builder()
                .merchantId(merchantId)
                .totalGrossRevenue(safeGross)
                .totalPlatformFee(totalFee != null ? totalFee : BigDecimal.ZERO)
                .totalNetRevenue(safeNet)
                .currentWalletBalance(currentBalance)
                .totalRevenue(safeGross)
                .walletBalance(currentBalance)
                .completedBookings(completedBookings)
                .platformFeePercent(platformFeePercent)
                .minWithdrawalAmount(BigDecimal.valueOf(50000))
                .series(series)
                .build();
    }

    private List<RevenueSeriesPointDto> calculateRevenueSeries(Long merchantId, Long parkingLotId, String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDate startLocalDate = today.minusDays(6);
        LocalDate endLocalDate = today;

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        boolean isExplicitDateRange = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        if (!isExplicitDateRange) {
            LocalDateTime latestCreatedAt = parkingLotId != null
                    ? paymentRepository.findLatestPaymentCreatedAtByMerchantIdAndParkingLotId(merchantId, parkingLotId)
                    : paymentRepository.findLatestPaymentCreatedAtByMerchantId(merchantId);

            if (latestCreatedAt != null) {
                LocalDate latestDate = latestCreatedAt.toLocalDate();
                if (latestDate.isBefore(today.minusDays(6))) {
                    endLocalDate = latestDate;
                    startLocalDate = latestDate.minusDays(6);
                }
            }
        }

        if (startDate != null && !startDate.isBlank()) {
            try {
                startLocalDate = LocalDate.parse(startDate.length() > 10 ? startDate.substring(0, 10) : startDate, formatter);
            } catch (Exception e) {
                log.warn("Invalid startDate format: {}, fallback to default 7 days ago", startDate);
            }
        }
        if (endDate != null && !endDate.isBlank()) {
            try {
                endLocalDate = LocalDate.parse(endDate.length() > 10 ? endDate.substring(0, 10) : endDate, formatter);
            } catch (Exception e) {
                log.warn("Invalid endDate format: {}, fallback to today", endDate);
            }
        }

        if (startLocalDate.isAfter(endLocalDate)) {
            LocalDate temp = startLocalDate;
            startLocalDate = endLocalDate;
            endLocalDate = temp;
        }

        LocalDateTime rangeStart = startLocalDate.atStartOfDay();
        LocalDateTime rangeEnd = endLocalDate.atTime(23, 59, 59, 999999999);

        Map<String, RevenueSeriesPointDto> pointsMap = new LinkedHashMap<>();
        LocalDate curr = startLocalDate;
        while (!curr.isAfter(endLocalDate)) {
            String periodKey = curr.format(formatter);
            pointsMap.put(periodKey, RevenueSeriesPointDto.builder()
                    .period(periodKey)
                    .revenue(BigDecimal.ZERO)
                    .bookingCount(0L)
                    .build());
            curr = curr.plusDays(1);
        }

        List<Payment> payments;
        if (parkingLotId != null) {
            payments = paymentRepository.findByMerchantIdAndParkingLotIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                    merchantId, parkingLotId, PaymentStatusEnum.SUCCESS, rangeStart, rangeEnd);
        } else {
            payments = paymentRepository.findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                    merchantId, PaymentStatusEnum.SUCCESS, rangeStart, rangeEnd);
        }

        if (payments != null && !payments.isEmpty()) {
            Map<String, List<Payment>> paymentsByDay = payments.stream()
                    .filter(p -> p.getCreatedAt() != null)
                    .collect(Collectors.groupingBy(p -> p.getCreatedAt().toLocalDate().format(formatter)));

            BigDecimal feePercent = platformFeePercent != null ? platformFeePercent : BigDecimal.valueOf(10.0);
            BigDecimal feeMultiplier = BigDecimal.valueOf(100).subtract(feePercent);

            paymentsByDay.forEach((dayKey, dayPayments) -> {
                BigDecimal dayNetTotal = BigDecimal.ZERO;
                Set<Long> uniqueBookingIds = new HashSet<>();

                for (Payment p : dayPayments) {
                    BigDecimal paymentGross = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                    BigDecimal net = paymentGross.multiply(feeMultiplier)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    dayNetTotal = dayNetTotal.add(net);
                    if (p.getBookingId() != null) {
                        uniqueBookingIds.add(p.getBookingId());
                    }
                }

                long bookingCount = uniqueBookingIds.isEmpty() ? dayPayments.size() : uniqueBookingIds.size();
                pointsMap.put(dayKey, RevenueSeriesPointDto.builder()
                        .period(dayKey)
                        .revenue(dayNetTotal)
                        .bookingCount(bookingCount)
                        .build());
            });
        }

        return new ArrayList<>(pointsMap.values());
    }

    private String buildSettlementJournalKey(Long merchantId, Long bookingId, Long paymentId) {
        if (paymentId != null) {
            return "SETTLE:PAYMENT:" + paymentId;
        }
        return "SETTLE:MERCHANT:" + merchantId + ":BOOKING:" + bookingId + ":" + UUID.randomUUID();
    }

    private Wallet getOrCreateWalletForUpdate(Long merchantId) {
        return walletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .merchantId(merchantId)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }
}
