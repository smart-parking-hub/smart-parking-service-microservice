package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.PaymentStatusEnum;
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
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository;
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

    @Autowired(required = false)
    private com.smartparking.payment.infrastructure.persistence.PaymentAttemptGuard attemptGuard;

    @Autowired(required = false)
    private CashCheckoutReceiptRepository cashCheckoutReceiptRepository;

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

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId) {
        return getRevenueSummary(merchantId, null, null, null, "DAY");
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantRevenueResponse getRevenueSummary(Long merchantId, Long parkingLotId, String startDate, String endDate, String granularity) {
        BigDecimal feePercent = platformFeePercent != null ? platformFeePercent : BigDecimal.valueOf(10.0);
        BigDecimal feeMultiplier = BigDecimal.valueOf(100).subtract(feePercent);

        BigDecimal totalGross;
        BigDecimal totalFee;
        BigDecimal totalNet;

        if (parkingLotId != null) {
            BigDecimal lotGross = paymentRepository.sumTotalRevenueByMerchantIdAndParkingLotId(merchantId, parkingLotId);
            totalGross = lotGross != null ? lotGross : BigDecimal.ZERO;
            totalNet = computeNet(totalGross, feeMultiplier);
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

        boolean isExplicitDateRange = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        LocalDate[] explicitRange = isExplicitDateRange ? parseDateRange(startDate, endDate) : null;

        List<PaymentRepository.ChannelAggregation> allTimeChannels =
                safeList(paymentRepository.aggregateSuccessByMerchantGroupedByLotAndChannel(merchantId));
        List<PaymentRepository.ChannelAggregation> periodChannels = explicitRange != null
                ? safeList(paymentRepository.aggregateSuccessByMerchantAndDateRangeGroupedByLotAndChannel(
                        merchantId,
                        explicitRange[0].atStartOfDay(),
                        explicitRange[1].atTime(23, 59, 59, 999999999)))
                : allTimeChannels;

        List<CashCheckoutReceiptRepository.LotCashAggregation> allTimeCash = cashCheckoutReceiptRepository != null
                ? safeList(cashCheckoutReceiptRepository.aggregateCompletedByMerchantGroupedByLot(merchantId))
                : Collections.emptyList();
        List<CashCheckoutReceiptRepository.LotCashAggregation> periodCash = cashCheckoutReceiptRepository != null
                ? (explicitRange != null
                        ? safeList(cashCheckoutReceiptRepository.aggregateCompletedByMerchantAndDateRangeGroupedByLot(
                                merchantId,
                                explicitRange[0].atStartOfDay(),
                                explicitRange[1].atTime(23, 59, 59, 999999999)))
                        : allTimeCash)
                : Collections.emptyList();

        Map<Long, LotRevenueBreakdownDto> lotMap = new TreeMap<>();

        BigDecimal onlineGrossTotal = BigDecimal.ZERO;
        BigDecimal onlineNetTotal = BigDecimal.ZERO;
        long onlineCountTotal = 0L;
        BigDecimal counterQrGrossTotal = BigDecimal.ZERO;
        BigDecimal counterQrNetTotal = BigDecimal.ZERO;
        long counterQrCountTotal = 0L;

        for (var row : allTimeChannels) {
            if (row == null) continue;
            BigDecimal gross = row.getTotalAmount() != null ? row.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal net = computeNet(gross, feeMultiplier);
            long count = row.getPaymentCount() != null ? row.getPaymentCount() : 0L;
            boolean isCounterQr = isCounterQr(row.getPaymentChannel());
            Long rowLotId = row.getParkingLotId();

            if (rowLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(rowLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                if (isCounterQr) {
                    lotDto.setCounterQrGrossRevenue(lotDto.getCounterQrGrossRevenue().add(gross));
                    lotDto.setCounterQrRevenue(lotDto.getCounterQrRevenue().add(net));
                    lotDto.setCounterQrCount(lotDto.getCounterQrCount() + count);
                } else {
                    lotDto.setOnlineTransferGrossRevenue(lotDto.getOnlineTransferGrossRevenue().add(gross));
                    lotDto.setOnlineTransferRevenue(lotDto.getOnlineTransferRevenue().add(net));
                    lotDto.setOnlineTransferCount(lotDto.getOnlineTransferCount() + count);
                }
            }

            if (parkingLotId == null || Objects.equals(parkingLotId, rowLotId)) {
                if (isCounterQr) {
                    counterQrGrossTotal = counterQrGrossTotal.add(gross);
                    counterQrNetTotal = counterQrNetTotal.add(net);
                    counterQrCountTotal += count;
                } else {
                    onlineGrossTotal = onlineGrossTotal.add(gross);
                    onlineNetTotal = onlineNetTotal.add(net);
                    onlineCountTotal += count;
                }
            }
        }

        BigDecimal periodOnlineNetTotal = BigDecimal.ZERO;
        long periodOnlineCountTotal = 0L;
        BigDecimal periodCounterQrNetTotal = BigDecimal.ZERO;
        long periodCounterQrCountTotal = 0L;

        for (var row : periodChannels) {
            if (row == null) continue;
            BigDecimal gross = row.getTotalAmount() != null ? row.getTotalAmount() : BigDecimal.ZERO;
            BigDecimal net = computeNet(gross, feeMultiplier);
            long count = row.getPaymentCount() != null ? row.getPaymentCount() : 0L;
            boolean isCounterQr = isCounterQr(row.getPaymentChannel());
            Long rowLotId = row.getParkingLotId();

            if (rowLotId != null) {
                LotRevenueBreakdownDto lotDto = lotMap.computeIfAbsent(rowLotId, id -> LotRevenueBreakdownDto.builder().parkingLotId(id).build());
                if (isCounterQr) {
                    lotDto.setPeriodCounterQrRevenue(lotDto.getPeriodCounterQrRevenue().add(net));
                    lotDto.setPeriodCounterQrCount(lotDto.getPeriodCounterQrCount() + count);
                } else {
                    lotDto.setPeriodOnlineTransferRevenue(lotDto.getPeriodOnlineTransferRevenue().add(net));
                    lotDto.setPeriodOnlineTransferCount(lotDto.getPeriodOnlineTransferCount() + count);
                }
            }

            if (parkingLotId == null || Objects.equals(parkingLotId, rowLotId)) {
                if (isCounterQr) {
                    periodCounterQrNetTotal = periodCounterQrNetTotal.add(net);
                    periodCounterQrCountTotal += count;
                } else {
                    periodOnlineNetTotal = periodOnlineNetTotal.add(net);
                    periodOnlineCountTotal += count;
                }
            }
        }

        BigDecimal totalCash = BigDecimal.ZERO;
        long totalCashCount = 0L;
        for (var row : allTimeCash) {
            if (row == null) continue;
            BigDecimal amount = row.getTotalAmount() != null ? row.getTotalAmount() : BigDecimal.ZERO;
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
            BigDecimal amount = row.getTotalAmount() != null ? row.getTotalAmount() : BigDecimal.ZERO;
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
                .onlineTransferGrossRevenue(onlineGrossTotal)
                .onlineTransferNetRevenue(onlineNetTotal)
                .onlineTransferCount(onlineCountTotal)
                .periodOnlineTransferNetRevenue(periodOnlineNetTotal)
                .periodOnlineTransferCount(periodOnlineCountTotal)
                .counterQrGrossRevenue(counterQrGrossTotal)
                .counterQrNetRevenue(counterQrNetTotal)
                .counterQrCount(counterQrCountTotal)
                .periodCounterQrNetRevenue(periodCounterQrNetTotal)
                .periodCounterQrCount(periodCounterQrCountTotal)
                .totalCashRevenue(totalCash)
                .totalCashBookings(totalCashCount)
                .periodCashRevenue(periodCashTotal)
                .periodCashBookings(periodCashCount)
                .revenueByParkingLot(new ArrayList<>(lotMap.values()))
                .series(series)
                .build();
    }

    private BigDecimal computeNet(BigDecimal gross, BigDecimal feeMultiplier) {
        if (gross == null || gross.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gross.multiply(feeMultiplier).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private boolean isCounterQr(String paymentChannel) {
        return "COUNTER_QR".equalsIgnoreCase(paymentChannel);
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

    private List<RevenueSeriesPointDto> calculateRevenueSeries(Long merchantId, Long parkingLotId, String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDate startLocalDate = today.minusDays(6);
        LocalDate endLocalDate = today;

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

        boolean isExplicitDateRange = (startDate != null && !startDate.isBlank()) || (endDate != null && !endDate.isBlank());
        if (!isExplicitDateRange) {
            LocalDateTime latestPaymentAt = parkingLotId != null
                    ? paymentRepository.findLatestPaymentCreatedAtByMerchantIdAndParkingLotId(merchantId, parkingLotId)
                    : paymentRepository.findLatestPaymentCreatedAtByMerchantId(merchantId);
            LocalDateTime latestCashAt = cashCheckoutReceiptRepository != null
                    ? (parkingLotId != null
                            ? cashCheckoutReceiptRepository.findLatestCompletedUpdatedAtByMerchantIdAndParkingLotId(merchantId, parkingLotId)
                            : cashCheckoutReceiptRepository.findLatestCompletedUpdatedAtByMerchantId(merchantId))
                    : null;

            LocalDateTime latestActivity = latestPaymentAt;
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
                    .cashRevenue(BigDecimal.ZERO)
                    .onlineTransferCount(0L)
                    .counterQrCount(0L)
                    .cashBookingCount(0L)
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

        List<CashCheckoutReceipt> cashReceipts = Collections.emptyList();
        if (cashCheckoutReceiptRepository != null) {
            cashReceipts = parkingLotId != null
                    ? safeList(cashCheckoutReceiptRepository.findByMerchantIdAndParkingLotIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                            merchantId, parkingLotId, "COMPLETED", rangeStart, rangeEnd))
                    : safeList(cashCheckoutReceiptRepository.findByMerchantIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                            merchantId, "COMPLETED", rangeStart, rangeEnd));
        }

        Map<String, List<Payment>> paymentsByDay = safeList(payments).stream()
                .filter(p -> p.getCreatedAt() != null)
                .collect(Collectors.groupingBy(p -> p.getCreatedAt().toLocalDate().format(formatter)));

        Map<String, List<CashCheckoutReceipt>> cashByDay = cashReceipts.stream()
                .filter(r -> r.getUpdatedAt() != null)
                .collect(Collectors.groupingBy(r -> r.getUpdatedAt().toLocalDate().format(formatter)));

        BigDecimal feePercent = platformFeePercent != null ? platformFeePercent : BigDecimal.valueOf(10.0);
        BigDecimal feeMultiplier = BigDecimal.valueOf(100).subtract(feePercent);

        Set<String> activeDays = new LinkedHashSet<>();
        activeDays.addAll(paymentsByDay.keySet());
        activeDays.addAll(cashByDay.keySet());

        for (String dayKey : activeDays) {
            List<Payment> dayPayments = paymentsByDay.getOrDefault(dayKey, Collections.emptyList());
            List<CashCheckoutReceipt> dayCash = cashByDay.getOrDefault(dayKey, Collections.emptyList());

            BigDecimal dayOnlineNet = BigDecimal.ZERO;
            BigDecimal dayCounterQrNet = BigDecimal.ZERO;
            long dayOnlineCount = 0L;
            long dayCounterQrCount = 0L;
            Set<Long> uniqueBookingIds = new HashSet<>();
            long anonymousCount = 0L;

            for (Payment p : dayPayments) {
                BigDecimal paymentGross = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
                BigDecimal net = computeNet(paymentGross, feeMultiplier);
                if (isCounterQr(p.getPaymentChannel())) {
                    dayCounterQrNet = dayCounterQrNet.add(net);
                    dayCounterQrCount++;
                } else {
                    dayOnlineNet = dayOnlineNet.add(net);
                    dayOnlineCount++;
                }
                if (p.getBookingId() != null) {
                    uniqueBookingIds.add(p.getBookingId());
                } else {
                    anonymousCount++;
                }
            }

            BigDecimal dayCashTotal = BigDecimal.ZERO;
            long dayCashCount = 0L;
            for (CashCheckoutReceipt r : dayCash) {
                BigDecimal amount = r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO;
                dayCashTotal = dayCashTotal.add(amount);
                dayCashCount++;
                if (r.getBookingId() != null) {
                    uniqueBookingIds.add(r.getBookingId());
                } else {
                    anonymousCount++;
                }
            }

            long bookingCount = uniqueBookingIds.size() + anonymousCount;
            BigDecimal daySystemNet = dayOnlineNet.add(dayCounterQrNet);

            pointsMap.put(dayKey, RevenueSeriesPointDto.builder()
                    .period(dayKey)
                    .revenue(daySystemNet)
                    .bookingCount(bookingCount)
                    .onlineTransferRevenue(dayOnlineNet)
                    .counterQrRevenue(dayCounterQrNet)
                    .cashRevenue(dayCashTotal)
                    .onlineTransferCount(dayOnlineCount)
                    .counterQrCount(dayCounterQrCount)
                    .cashBookingCount(dayCashCount)
                    .build());
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
