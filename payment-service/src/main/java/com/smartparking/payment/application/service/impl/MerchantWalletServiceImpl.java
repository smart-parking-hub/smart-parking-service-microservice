package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.LotRevenueBreakdownDto;
import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.dto.RevenueSeriesPointDto;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import com.smartparking.payment.domain.model.LedgerAccountCode;
import com.smartparking.payment.domain.model.LedgerJournal;
import com.smartparking.payment.domain.model.LedgerPosting;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.PostingDirection;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository;
import com.smartparking.payment.domain.repository.LedgerJournalRepository;
import com.smartparking.payment.domain.repository.LedgerPostingRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
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

    @Autowired(required = false)
    private com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;

    @Autowired(required = false)
    private CashCheckoutReceiptRepository cashCheckoutReceiptRepository;

    @Autowired(required = false)
    private RefundRequestRepository refundRequestRepository;

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
        if (attemptGuard != null && payment != null) attemptGuard.lock(bookingId, payment.getPaymentType());
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
                && (ledgerJournalRepository.existsByJournalKey("ESCROW:PAYMENT:" + paymentId)
                    || ledgerJournalRepository.existsByJournalKey("WALLET:CAPTURE:" + paymentId));
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

    public SettlementSplit refundSplit(Long paymentId, BigDecimal refund) {
        if (ledgerJournalRepository == null) return split(refund);
        var original = ledgerJournalRepository.findByJournalKey("SETTLE:PAYMENT:" + paymentId);
        if (original.isEmpty()) return split(refund);
        var journal = original.get();
        BigDecimal gross = journal.getPostings().stream().filter(p -> p.getDirection() == PostingDirection.DEBIT)
                .map(LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = journal.getPostings().stream().filter(p -> p.getAccountCode() == LedgerAccountCode.PLATFORM_FEE_REVENUE && p.getDirection() == PostingDirection.CREDIT)
                .map(LedgerPosting::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (gross.signum() <= 0 || refund.compareTo(gross) > 0) throw new AppException(ErrorCode.BAD_REQUEST, "Khoản hoàn vượt giá trị đã quyết toán");
        BigDecimal retainedFee = gross.subtract(refund).multiply(journal.getFeePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal reversedFee = fee.subtract(retainedFee).max(BigDecimal.ZERO);
        return SettlementSplit.builder().grossAmount(refund).feePercent(journal.getFeePercent())
                .feeAmount(reversedFee).netAmount(refund.subtract(reversedFee)).build();
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

        if (attemptGuard != null && bookingId != null) attemptGuard.lock(bookingId, com.smartparking.common.enums.PaymentTypeEnum.DEPOSIT);
        BigDecimal scaledRefund = refundAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal escrowPart = ledgerJournalRepository == null ? BigDecimal.ZERO : ledgerJournalRepository.findByPaymentId(paymentId).stream()
                .flatMap(j -> j.getPostings().stream()).filter(p -> p.getAccountCode() == LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW)
                .map(p -> p.getDirection() == PostingDirection.CREDIT ? p.getAmount() : p.getAmount().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add).max(BigDecimal.ZERO).min(scaledRefund);
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

        if (alreadySettledToMerchant && merchantId != null && escrowPart.compareTo(scaledRefund) < 0) {
            SettlementSplit reversalSplit = refundSplit(paymentId, scaledRefund.subtract(escrowPart));
            if (escrowPart.signum() > 0) journal.addPosting(LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW, merchantId, PostingDirection.DEBIT, escrowPart);
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
                    .grossAmount(reversalSplit.getGrossAmount())
                    .payment(paymentRepository.getReferenceById(paymentId))
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
        if (original.getDriverId() != null) throw new AppException(ErrorCode.BAD_REQUEST, "Bút toán ví tài xế phải được xử lý qua luồng hoàn hoặc đối soát ví");
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

    private enum ChannelBucket {
        ONLINE_TRANSFER,
        COUNTER_QR,
        UNKNOWN
    }

    private record SeriesComputation(List<RevenueSeriesPointDto> points, long distinctPeriodBookings) {}

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId) {
        return getRevenueSummary(merchantId, null, null, null, "DAY");
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId, Long parkingLotId, String startDate, String endDate, String granularity) {
        BigDecimal currentBalance = getWalletBalance(merchantId);

        boolean isExplicitDateRange = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        LocalDate[] explicitRange = isExplicitDateRange ? parseDateRange(startDate, endDate) : null;
        LocalDateTime explicitStart = explicitRange != null ? explicitRange[0].atStartOfDay() : null;
        LocalDateTime explicitEnd = explicitRange != null ? explicitRange[1].atTime(23, 59, 59, 999999999) : null;

        List<WalletLedgerEntry> ledgerEntries = safeList(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId));
        List<Payment> successPayments = safeList(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS));

        Map<Long, Payment> paymentByBookingId = new HashMap<>();
        Set<Long> settledPaymentIds = new HashSet<>();
        for (WalletLedgerEntry entry : ledgerEntries) {
            if (entry == null) continue;
            Payment p = entry.getPayment();
            if (p != null) {
                if (entry.getEntryType() == WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT && p.getId() != null) {
                    settledPaymentIds.add(p.getId());
                }
                if (p.getBookingId() != null) {
                    paymentByBookingId.putIfAbsent(p.getBookingId(), p);
                }
            }
        }
        for (Payment p : successPayments) {
            if (p != null && p.getBookingId() != null) {
                paymentByBookingId.putIfAbsent(p.getBookingId(), p);
            }
        }

        Map<Long, LotRevenueBreakdownDto> lotMap = new TreeMap<>();

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal periodTotalGross = BigDecimal.ZERO;
        BigDecimal periodTotalFee = BigDecimal.ZERO;
        BigDecimal periodTotalNet = BigDecimal.ZERO;

        BigDecimal onlineGrossTotal = BigDecimal.ZERO;
        BigDecimal onlineNetTotal = BigDecimal.ZERO;
        long onlineCountTotal = 0L;
        BigDecimal periodOnlineGrossTotal = BigDecimal.ZERO;
        BigDecimal periodOnlineNetTotal = BigDecimal.ZERO;
        long periodOnlineCountTotal = 0L;

        BigDecimal counterQrGrossTotal = BigDecimal.ZERO;
        BigDecimal counterQrNetTotal = BigDecimal.ZERO;
        long counterQrCountTotal = 0L;
        BigDecimal periodCounterQrGrossTotal = BigDecimal.ZERO;
        BigDecimal periodCounterQrNetTotal = BigDecimal.ZERO;
        long periodCounterQrCountTotal = 0L;

        BigDecimal unclassifiedGrossTotal = BigDecimal.ZERO;
        BigDecimal unclassifiedNetTotal = BigDecimal.ZERO;
        long unclassifiedCountTotal = 0L;
        BigDecimal periodUnclassifiedGrossTotal = BigDecimal.ZERO;
        BigDecimal periodUnclassifiedNetTotal = BigDecimal.ZERO;
        long periodUnclassifiedCountTotal = 0L;

        List<WalletLedgerEntry> scopedLedgerEntries = new ArrayList<>();
        LocalDateTime latestScopedLedgerAt = null;

        for (WalletLedgerEntry entry : ledgerEntries) {
            if (entry == null) continue;
            BigDecimal gross = resolveSignedGross(entry);
            BigDecimal fee = safeAmount(entry.getFeeAmount());
            BigDecimal net = safeAmount(entry.getNetAmount());
            boolean isSettlementCredit = entry.getEntryType() == WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT;
            long countDelta = isSettlementCredit ? 1L : 0L;

            Payment linkedPayment = entry.getPayment() != null
                    ? entry.getPayment()
                    : (entry.getBookingId() != null ? paymentByBookingId.get(entry.getBookingId()) : null);
            Long entryLotId = linkedPayment != null ? linkedPayment.getParkingLotId() : null;
            ChannelBucket channel = resolveChannel(linkedPayment != null ? linkedPayment.getPaymentChannel() : null);
            LocalDateTime businessTime = resolveLedgerTimestamp(entry, linkedPayment);
            boolean inPeriod = explicitRange == null || isWithinRange(businessTime, explicitStart, explicitEnd);

            if (entryLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(entryLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                accumulateLotChannel(lotDto, channel, gross, net, countDelta, false);
                if (inPeriod) {
                    accumulateLotChannel(lotDto, channel, gross, net, countDelta, true);
                }
            }

            if (parkingLotId == null || Objects.equals(parkingLotId, entryLotId)) {
                scopedLedgerEntries.add(entry);
                if (businessTime != null && (latestScopedLedgerAt == null || businessTime.isAfter(latestScopedLedgerAt))) {
                    latestScopedLedgerAt = businessTime;
                }

                totalGross = totalGross.add(gross);
                totalFee = totalFee.add(fee);
                totalNet = totalNet.add(net);
                switch (channel) {
                    case ONLINE_TRANSFER -> {
                        onlineGrossTotal = onlineGrossTotal.add(gross);
                        onlineNetTotal = onlineNetTotal.add(net);
                        onlineCountTotal += countDelta;
                    }
                    case COUNTER_QR -> {
                        counterQrGrossTotal = counterQrGrossTotal.add(gross);
                        counterQrNetTotal = counterQrNetTotal.add(net);
                        counterQrCountTotal += countDelta;
                    }
                    case UNKNOWN -> {
                        unclassifiedGrossTotal = unclassifiedGrossTotal.add(gross);
                        unclassifiedNetTotal = unclassifiedNetTotal.add(net);
                        unclassifiedCountTotal += countDelta;
                    }
                }

                if (inPeriod) {
                    periodTotalGross = periodTotalGross.add(gross);
                    periodTotalFee = periodTotalFee.add(fee);
                    periodTotalNet = periodTotalNet.add(net);
                    switch (channel) {
                        case ONLINE_TRANSFER -> {
                            periodOnlineGrossTotal = periodOnlineGrossTotal.add(gross);
                            periodOnlineNetTotal = periodOnlineNetTotal.add(net);
                            periodOnlineCountTotal += countDelta;
                        }
                        case COUNTER_QR -> {
                            periodCounterQrGrossTotal = periodCounterQrGrossTotal.add(gross);
                            periodCounterQrNetTotal = periodCounterQrNetTotal.add(net);
                            periodCounterQrCountTotal += countDelta;
                        }
                        case UNKNOWN -> {
                            periodUnclassifiedGrossTotal = periodUnclassifiedGrossTotal.add(gross);
                            periodUnclassifiedNetTotal = periodUnclassifiedNetTotal.add(net);
                            periodUnclassifiedCountTotal += countDelta;
                        }
                    }
                }
            }
        }

        // Compute pre-settlement (escrow) collected gross separately, deducting valid refunds just like DailySettlementServiceImpl.
        List<Payment> unsettledPayments = new ArrayList<>();
        Set<Long> unsettledBookingIds = new HashSet<>();
        for (Payment p : successPayments) {
            if (p == null) continue;
            if (p.getId() != null && settledPaymentIds.contains(p.getId())) continue;
            unsettledPayments.add(p);
            if (p.getBookingId() != null) {
                unsettledBookingIds.add(p.getBookingId());
            }
        }

        Map<Long, RefundRequest> refundsByBookingId = Collections.emptyMap();
        if (!unsettledBookingIds.isEmpty() && refundRequestRepository != null) {
            List<RefundRequest> refunds = safeList(refundRequestRepository.findByBookingIdIn(unsettledBookingIds));
            refundsByBookingId = new HashMap<>();
            for (RefundRequest r : refunds) {
                if (r != null && r.getBookingId() != null) {
                    refundsByBookingId.put(r.getBookingId(), r);
                }
            }
        }

        BigDecimal pendingSettlementGrossTotal = BigDecimal.ZERO;
        BigDecimal periodPendingSettlementGrossTotal = BigDecimal.ZERO;
        for (Payment p : unsettledPayments) {
            BigDecimal settleableGross = computeSettleableGross(p, refundsByBookingId);
            if (settleableGross.signum() <= 0) continue;

            boolean inPeriod = explicitRange == null || isWithinRange(p.getCreatedAt(), explicitStart, explicitEnd);
            Long rowLotId = p.getParkingLotId();
            if (rowLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(rowLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                lotDto.setPendingSettlementGrossRevenue(lotDto.getPendingSettlementGrossRevenue().add(settleableGross));
                if (inPeriod) {
                    lotDto.setPeriodPendingSettlementGrossRevenue(lotDto.getPeriodPendingSettlementGrossRevenue().add(settleableGross));
                }
            }
            if (parkingLotId == null || Objects.equals(parkingLotId, rowLotId)) {
                pendingSettlementGrossTotal = pendingSettlementGrossTotal.add(settleableGross);
                if (inPeriod) {
                    periodPendingSettlementGrossTotal = periodPendingSettlementGrossTotal.add(settleableGross);
                }
            }
        }

        List<CashCheckoutReceiptRepository.LotCashAggregation> allTimeCash = cashCheckoutReceiptRepository != null
                ? safeList(cashCheckoutReceiptRepository.aggregateCompletedByMerchantGroupedByLot(merchantId))
                : Collections.emptyList();
        List<CashCheckoutReceiptRepository.LotCashAggregation> periodCash = cashCheckoutReceiptRepository != null
                ? (explicitRange != null
                        ? safeList(cashCheckoutReceiptRepository.aggregateCompletedByMerchantAndDateRangeGroupedByLot(
                                merchantId, explicitStart, explicitEnd))
                        : allTimeCash)
                : Collections.emptyList();

        BigDecimal totalCash = BigDecimal.ZERO;
        long totalCashCount = 0L;
        for (var row : allTimeCash) {
            if (row == null) continue;
            BigDecimal amount = safeAmount(row.getTotalAmount());
            long count = row.getBookingCount() != null ? row.getBookingCount() : 0L;
            Long rowLotId = row.getParkingLotId();

            if (rowLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(rowLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                lotDto.setCashRevenue(lotDto.getCashRevenue().add(amount));
                lotDto.setCashBookingCount(lotDto.getCashBookingCount() + count);
            }

            if (parkingLotId == null || Objects.equals(parkingLotId, rowLotId)) {
                totalCash = totalCash.add(amount);
                totalCashCount += count;
            }
        }

        BigDecimal periodCashTotal = BigDecimal.ZERO;
        long periodCashCount = 0L;
        for (var row : periodCash) {
            if (row == null) continue;
            BigDecimal amount = safeAmount(row.getTotalAmount());
            long count = row.getBookingCount() != null ? row.getBookingCount() : 0L;
            Long rowLotId = row.getParkingLotId();

            if (rowLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(rowLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                lotDto.setPeriodCashRevenue(lotDto.getPeriodCashRevenue().add(amount));
                lotDto.setPeriodCashBookingCount(lotDto.getPeriodCashBookingCount() + count);
            }

            if (parkingLotId == null || Objects.equals(parkingLotId, rowLotId)) {
                periodCashTotal = periodCashTotal.add(amount);
                periodCashCount += count;
            }
        }

        SeriesComputation seriesComputation = calculateRevenueSeries(
                merchantId, parkingLotId, startDate, endDate, scopedLedgerEntries, paymentByBookingId, latestScopedLedgerAt);

        return MerchantRevenueResponse.builder()
                .merchantId(merchantId)
                .totalGrossRevenue(totalGross)
                .totalPlatformFee(totalFee)
                .totalNetRevenue(totalNet)
                .periodTotalGrossRevenue(periodTotalGross)
                .periodTotalPlatformFee(periodTotalFee)
                .periodTotalNetRevenue(periodTotalNet)
                .pendingSettlementGrossRevenue(pendingSettlementGrossTotal)
                .periodPendingSettlementGrossRevenue(periodPendingSettlementGrossTotal)
                .currentWalletBalance(currentBalance)
                .totalRevenue(totalGross)
                .walletBalance(currentBalance)
                .completedBookings(seriesComputation.distinctPeriodBookings())
                .platformFeePercent(platformFeePercent)
                .minWithdrawalAmount(BigDecimal.valueOf(50000))
                .onlineTransferGrossRevenue(onlineGrossTotal)
                .onlineTransferNetRevenue(onlineNetTotal)
                .onlineTransferCount(onlineCountTotal)
                .periodOnlineTransferGrossRevenue(periodOnlineGrossTotal)
                .periodOnlineTransferNetRevenue(periodOnlineNetTotal)
                .periodOnlineTransferCount(periodOnlineCountTotal)
                .counterQrGrossRevenue(counterQrGrossTotal)
                .counterQrNetRevenue(counterQrNetTotal)
                .counterQrCount(counterQrCountTotal)
                .periodCounterQrGrossRevenue(periodCounterQrGrossTotal)
                .periodCounterQrNetRevenue(periodCounterQrNetTotal)
                .periodCounterQrCount(periodCounterQrCountTotal)
                .unclassifiedTransferGrossRevenue(unclassifiedGrossTotal)
                .unclassifiedTransferNetRevenue(unclassifiedNetTotal)
                .unclassifiedTransferCount(unclassifiedCountTotal)
                .periodUnclassifiedTransferGrossRevenue(periodUnclassifiedGrossTotal)
                .periodUnclassifiedTransferNetRevenue(periodUnclassifiedNetTotal)
                .periodUnclassifiedTransferCount(periodUnclassifiedCountTotal)
                .totalCashRevenue(totalCash)
                .totalCashBookings(totalCashCount)
                .periodCashRevenue(periodCashTotal)
                .periodCashBookings(periodCashCount)
                .revenueByParkingLot(new ArrayList<>(lotMap.values()))
                .series(seriesComputation.points())
                .build();
    }

    private void accumulateLotChannel(
            LotRevenueBreakdownDto lotDto,
            ChannelBucket channel,
            BigDecimal gross,
            BigDecimal net,
            long countDelta,
            boolean isPeriod) {
        if (isPeriod) {
            switch (channel) {
                case ONLINE_TRANSFER -> {
                    lotDto.setPeriodOnlineTransferGrossRevenue(lotDto.getPeriodOnlineTransferGrossRevenue().add(gross));
                    lotDto.setPeriodOnlineTransferRevenue(lotDto.getPeriodOnlineTransferRevenue().add(net));
                    lotDto.setPeriodOnlineTransferCount(lotDto.getPeriodOnlineTransferCount() + countDelta);
                }
                case COUNTER_QR -> {
                    lotDto.setPeriodCounterQrGrossRevenue(lotDto.getPeriodCounterQrGrossRevenue().add(gross));
                    lotDto.setPeriodCounterQrRevenue(lotDto.getPeriodCounterQrRevenue().add(net));
                    lotDto.setPeriodCounterQrCount(lotDto.getPeriodCounterQrCount() + countDelta);
                }
                case UNKNOWN -> {
                    lotDto.setPeriodUnclassifiedTransferGrossRevenue(lotDto.getPeriodUnclassifiedTransferGrossRevenue().add(gross));
                    lotDto.setPeriodUnclassifiedTransferRevenue(lotDto.getPeriodUnclassifiedTransferRevenue().add(net));
                    lotDto.setPeriodUnclassifiedTransferCount(lotDto.getPeriodUnclassifiedTransferCount() + countDelta);
                }
            }
        } else {
            switch (channel) {
                case ONLINE_TRANSFER -> {
                    lotDto.setOnlineTransferGrossRevenue(lotDto.getOnlineTransferGrossRevenue().add(gross));
                    lotDto.setOnlineTransferRevenue(lotDto.getOnlineTransferRevenue().add(net));
                    lotDto.setOnlineTransferCount(lotDto.getOnlineTransferCount() + countDelta);
                }
                case COUNTER_QR -> {
                    lotDto.setCounterQrGrossRevenue(lotDto.getCounterQrGrossRevenue().add(gross));
                    lotDto.setCounterQrRevenue(lotDto.getCounterQrRevenue().add(net));
                    lotDto.setCounterQrCount(lotDto.getCounterQrCount() + countDelta);
                }
                case UNKNOWN -> {
                    lotDto.setUnclassifiedTransferGrossRevenue(lotDto.getUnclassifiedTransferGrossRevenue().add(gross));
                    lotDto.setUnclassifiedTransferRevenue(lotDto.getUnclassifiedTransferRevenue().add(net));
                    lotDto.setUnclassifiedTransferCount(lotDto.getUnclassifiedTransferCount() + countDelta);
                }
            }
        }
    }

    private BigDecimal computeSettleableGross(Payment payment, Map<Long, RefundRequest> refundsByBookingId) {
        if (payment == null || payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal gross = payment.getAmount();
        if (payment.getBookingId() != null && refundsByBookingId != null) {
            RefundRequest refund = refundsByBookingId.get(payment.getBookingId());
            if (refund != null
                    && !"FAILED".equals(refund.getStatus())
                    && !"NONE".equals(refund.getStatus())
                    && refund.getRefundAmount() != null
                    && refund.getRefundAmount().signum() > 0) {
                if (refund.getPaymentId() == null && payment.getPaymentType() == PaymentTypeEnum.DEPOSIT) {
                    return BigDecimal.ZERO;
                }
                if (Objects.equals(refund.getPaymentId(), payment.getId())) {
                    gross = gross.subtract(refund.getRefundAmount()).max(BigDecimal.ZERO);
                }
            }
        }
        return gross;
    }

    private LocalDateTime resolveLedgerTimestamp(WalletLedgerEntry entry, Payment linkedPayment) {
        if (entry.getEntryType() == WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT
                && entry.getPayment() != null
                && entry.getPayment().getCreatedAt() != null) {
            return entry.getPayment().getCreatedAt();
        }
        if (entry.getCreatedAt() != null) {
            return entry.getCreatedAt();
        }
        return linkedPayment != null ? linkedPayment.getCreatedAt() : null;
    }

    private boolean isWithinRange(LocalDateTime ts, LocalDateTime start, LocalDateTime end) {
        if (ts == null) return false;
        return !ts.isBefore(start) && !ts.isAfter(end);
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private BigDecimal resolveSignedGross(WalletLedgerEntry entry) {
        BigDecimal gross = safeAmount(entry.getGrossAmount());
        if (entry.getEntryType() == WalletLedgerEntryTypeEnum.ADJUSTMENT) {
            BigDecimal fee = safeAmount(entry.getFeeAmount());
            BigDecimal net = safeAmount(entry.getNetAmount());
            // Post-settlement refund adjustments persist grossAmount as positive magnitude while fee/net are negative.
            if (gross.signum() > 0 && (net.signum() < 0 || fee.signum() < 0)) {
                return gross.negate();
            }
        }
        return gross;
    }

    private ChannelBucket resolveChannel(String paymentChannel) {
        if ("COUNTER_QR".equalsIgnoreCase(paymentChannel)) {
            return ChannelBucket.COUNTER_QR;
        }
        if ("ONLINE_TRANSFER".equalsIgnoreCase(paymentChannel)) {
            return ChannelBucket.ONLINE_TRANSFER;
        }
        return ChannelBucket.UNKNOWN;
    }

    private <T> List<T> safeList(List<T> input) {
        return input != null ? input : Collections.emptyList();
    }

    private LocalDate[] parseDateRange(String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDate startLocalDate = today.minusDays(6);
        LocalDate endLocalDate = today;
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

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
        return new LocalDate[]{startLocalDate, endLocalDate};
    }

    private SeriesComputation calculateRevenueSeries(
            Long merchantId,
            Long parkingLotId,
            String startDate,
            String endDate,
            List<WalletLedgerEntry> scopedLedgerEntries,
            Map<Long, Payment> paymentByBookingId,
            LocalDateTime latestScopedLedgerAt) {
        LocalDate today = LocalDate.now();
        LocalDate startLocalDate = today.minusDays(6);
        LocalDate endLocalDate = today;

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        boolean isExplicitDateRange = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        if (!isExplicitDateRange) {
            LocalDateTime latestCashAt = cashCheckoutReceiptRepository != null
                    ? (parkingLotId != null
                            ? cashCheckoutReceiptRepository.findLatestCompletedUpdatedAtByMerchantIdAndParkingLotId(merchantId, parkingLotId)
                            : cashCheckoutReceiptRepository.findLatestCompletedUpdatedAtByMerchantId(merchantId))
                    : null;

            LocalDateTime latestActivity = latestScopedLedgerAt;
            if (latestCashAt != null && (latestActivity == null || latestCashAt.isAfter(latestActivity))) {
                latestActivity = latestCashAt;
            }

            if (latestActivity != null) {
                LocalDate latestDate = latestActivity.toLocalDate();
                if (latestDate.isBefore(today.minusDays(6))) {
                    endLocalDate = latestDate;
                    startLocalDate = latestDate.minusDays(6);
                }
            }
        } else {
            LocalDate[] parsed = parseDateRange(startDate, endDate);
            startLocalDate = parsed[0];
            endLocalDate = parsed[1];
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
                    .onlineTransferRevenue(BigDecimal.ZERO)
                    .counterQrRevenue(BigDecimal.ZERO)
                    .unclassifiedTransferRevenue(BigDecimal.ZERO)
                    .cashRevenue(BigDecimal.ZERO)
                    .onlineTransferCount(0L)
                    .counterQrCount(0L)
                    .unclassifiedTransferCount(0L)
                    .cashBookingCount(0L)
                    .build());
            curr = curr.plusDays(1);
        }

        Map<String, List<WalletLedgerEntry>> ledgerByDay = new LinkedHashMap<>();
        for (WalletLedgerEntry entry : safeList(scopedLedgerEntries)) {
            Payment linkedPayment = entry.getPayment() != null
                    ? entry.getPayment()
                    : (entry.getBookingId() != null ? paymentByBookingId.get(entry.getBookingId()) : null);
            LocalDateTime ts = resolveLedgerTimestamp(entry, linkedPayment);
            if (!isWithinRange(ts, rangeStart, rangeEnd)) continue;
            String dayKey = ts.toLocalDate().format(formatter);
            ledgerByDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(entry);
        }

        List<CashCheckoutReceipt> cashReceipts = Collections.emptyList();
        if (cashCheckoutReceiptRepository != null) {
            cashReceipts = parkingLotId != null
                    ? safeList(cashCheckoutReceiptRepository.findByMerchantIdAndParkingLotIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                            merchantId, parkingLotId, "COMPLETED", rangeStart, rangeEnd))
                    : safeList(cashCheckoutReceiptRepository.findByMerchantIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                            merchantId, "COMPLETED", rangeStart, rangeEnd));
        }

        Map<String, List<CashCheckoutReceipt>> cashByDay = cashReceipts.stream()
                .filter(r -> r.getUpdatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getUpdatedAt().toLocalDate().format(formatter)));

        Set<String> activeDays = new LinkedHashSet<>();
        activeDays.addAll(ledgerByDay.keySet());
        activeDays.addAll(cashByDay.keySet());

        // Period-wide booking deduplication prevents overnight deposit + next-day cash from double counting.
        Set<Long> periodUniqueBookingIds = new HashSet<>();
        long periodAnonymousCount = 0L;

        for (String dayKey : activeDays) {
            List<WalletLedgerEntry> dayLedger = ledgerByDay.getOrDefault(dayKey, Collections.emptyList());
            List<CashCheckoutReceipt> dayCash = cashByDay.getOrDefault(dayKey, Collections.emptyList());

            BigDecimal dayOnlineNet = BigDecimal.ZERO;
            BigDecimal dayCounterQrNet = BigDecimal.ZERO;
            BigDecimal dayUnclassifiedNet = BigDecimal.ZERO;
            long dayOnlineCount = 0L;
            long dayCounterQrCount = 0L;
            long dayUnclassifiedCount = 0L;
            Set<Long> dayUniqueBookingIds = new HashSet<>();
            long dayAnonymousCount = 0L;

            for (WalletLedgerEntry entry : dayLedger) {
                BigDecimal net = safeAmount(entry.getNetAmount());
                boolean isSettlementCredit = entry.getEntryType() == WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT;
                long countDelta = isSettlementCredit ? 1L : 0L;
                Payment linkedPayment = entry.getPayment() != null
                        ? entry.getPayment()
                        : (entry.getBookingId() != null ? paymentByBookingId.get(entry.getBookingId()) : null);
                ChannelBucket channel = resolveChannel(linkedPayment != null ? linkedPayment.getPaymentChannel() : null);

                switch (channel) {
                    case ONLINE_TRANSFER -> {
                        dayOnlineNet = dayOnlineNet.add(net);
                        dayOnlineCount += countDelta;
                    }
                    case COUNTER_QR -> {
                        dayCounterQrNet = dayCounterQrNet.add(net);
                        dayCounterQrCount += countDelta;
                    }
                    case UNKNOWN -> {
                        dayUnclassifiedNet = dayUnclassifiedNet.add(net);
                        dayUnclassifiedCount += countDelta;
                    }
                }

                Long bookingId = entry.getBookingId() != null
                        ? entry.getBookingId()
                        : (linkedPayment != null ? linkedPayment.getBookingId() : null);
                if (bookingId != null) {
                    dayUniqueBookingIds.add(bookingId);
                    periodUniqueBookingIds.add(bookingId);
                } else if (isSettlementCredit) {
                    dayAnonymousCount++;
                    periodAnonymousCount++;
                }
            }

            BigDecimal dayCashTotal = BigDecimal.ZERO;
            long dayCashCount = 0L;
            for (CashCheckoutReceipt r : dayCash) {
                BigDecimal amount = safeAmount(r.getAmount());
                dayCashTotal = dayCashTotal.add(amount);
                dayCashCount++;
                if (r.getBookingId() != null) {
                    dayUniqueBookingIds.add(r.getBookingId());
                    periodUniqueBookingIds.add(r.getBookingId());
                } else {
                    dayAnonymousCount++;
                    periodAnonymousCount++;
                }
            }

            long dayBookingCount = dayUniqueBookingIds.size() + dayAnonymousCount;
            BigDecimal daySystemNet = dayOnlineNet.add(dayCounterQrNet).add(dayUnclassifiedNet);

            pointsMap.put(dayKey, RevenueSeriesPointDto.builder()
                    .period(dayKey)
                    .revenue(daySystemNet)
                    .bookingCount(dayBookingCount)
                    .onlineTransferRevenue(dayOnlineNet)
                    .counterQrRevenue(dayCounterQrNet)
                    .unclassifiedTransferRevenue(dayUnclassifiedNet)
                    .cashRevenue(dayCashTotal)
                    .onlineTransferCount(dayOnlineCount)
                    .counterQrCount(dayCounterQrCount)
                    .unclassifiedTransferCount(dayUnclassifiedCount)
                    .cashBookingCount(dayCashCount)
                    .build());
        }

        long distinctPeriodBookings = periodUniqueBookingIds.size() + periodAnonymousCount;
        return new SeriesComputation(new ArrayList<>(pointsMap.values()), distinctPeriodBookings);
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
