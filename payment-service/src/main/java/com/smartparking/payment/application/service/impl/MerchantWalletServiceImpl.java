package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.MerchantRevenueResponse;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.MerchantWalletService;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantWalletServiceImpl implements MerchantWalletService {

    @Value("${app.platform.fee-percent:10.0}")
    private BigDecimal platformFeePercent;

    private final WalletRepository walletRepository;
    private final WalletLedgerEntryRepository ledgerRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public SettlementSplit split(BigDecimal grossAmount) {
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return SettlementSplit.builder()
                    .grossAmount(BigDecimal.ZERO)
                    .feePercent(platformFeePercent)
                    .feeAmount(BigDecimal.ZERO)
                    .netAmount(BigDecimal.ZERO)
                    .build();
        }

        BigDecimal feeRate = platformFeePercent.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal feeAmount = grossAmount.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = grossAmount.subtract(feeAmount);

        return SettlementSplit.builder()
                .grossAmount(grossAmount)
                .feePercent(platformFeePercent)
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .build();
    }

    @Override
    @Transactional
    public SettlementSplit creditSettlement(Long merchantId, Long bookingId, Payment payment, BigDecimal grossAmount, String note) {
        SettlementSplit split = split(grossAmount);
        Wallet wallet = getOrCreateWalletForUpdate(merchantId);

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

        wallet.setBalance(wallet.getBalance().add(split.getNetAmount()));
        walletRepository.save(wallet);

        log.info("Credited settlement to merchant {} for booking {}: net={} fee={}",
                merchantId, bookingId, split.getNetAmount(), split.getFeeAmount());
        return split;
    }

    @Override
    @Transactional
    public void holdWithdrawal(Long merchantId, BigDecimal amount, String note) {
        Wallet wallet = getOrCreateWalletForUpdate(merchantId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Số dư khả dụng không đủ để rút tiền!");
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

        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);

        log.info("Held withdrawal amount {} from merchant {}", amount, merchantId);
    }

    @Override
    @Transactional
    public void refundWithdrawal(Long merchantId, BigDecimal amount, String note) {
        Wallet wallet = getOrCreateWalletForUpdate(merchantId);

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

        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        log.info("Refunded withdrawal amount {} to merchant {}", amount, merchantId);
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
    public MerchantRevenueResponse getRevenueSummary(Long merchantId) {
        BigDecimal totalGross = paymentRepository.sumTotalRevenueByMerchantId(merchantId);
        BigDecimal totalFee = ledgerRepository.sumPlatformFeeByMerchantId(merchantId);
        BigDecimal totalNet = ledgerRepository.sumNetAmountByMerchantId(merchantId);
        BigDecimal currentBalance = getWalletBalance(merchantId);
        BigDecimal safeGross = totalGross != null ? totalGross : BigDecimal.ZERO;
        BigDecimal safeNet = totalNet != null ? totalNet : BigDecimal.ZERO;

        return MerchantRevenueResponse.builder()
                .merchantId(merchantId)
                .totalGrossRevenue(safeGross)
                .totalPlatformFee(totalFee != null ? totalFee : BigDecimal.ZERO)
                .totalNetRevenue(safeNet)
                .currentWalletBalance(currentBalance)
                .totalRevenue(safeGross)
                .walletBalance(currentBalance)
                .completedBookings(0L)
                .platformFeePercent(platformFeePercent)
                .minWithdrawalAmount(BigDecimal.valueOf(50000))
                .series(java.util.Collections.emptyList())
                .build();
    }

    private Wallet getOrCreateWalletForUpdate(Long merchantId) {
        return walletRepository.findByMerchantIdForUpdate(merchantId)
                .orElseGet(() -> walletRepository.save(Wallet.builder()
                        .merchantId(merchantId)
                        .balance(BigDecimal.ZERO)
                        .build()));
    }
}
