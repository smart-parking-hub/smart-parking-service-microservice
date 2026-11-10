package com.smartparking.payment.application.service.impl;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.domain.model.*;
import com.smartparking.payment.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoubleEntryLedgerAndSettlementTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository ledgerRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private LedgerJournalRepository ledgerJournalRepository;

    @Mock
    private LedgerPostingRepository ledgerPostingRepository;

    @Mock
    private SettlementBatchRepository settlementBatchRepository;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    private MerchantWalletServiceImpl merchantWalletService;
    private DailySettlementServiceImpl dailySettlementService;

    @BeforeEach
    void setUp() {
        merchantWalletService = new MerchantWalletServiceImpl(walletRepository, ledgerRepository, paymentRepository);
        ReflectionTestUtils.setField(merchantWalletService, "platformFeePercent", new BigDecimal("10.0"));
        ReflectionTestUtils.setField(merchantWalletService, "ledgerJournalRepository", ledgerJournalRepository);
        ReflectionTestUtils.setField(merchantWalletService, "ledgerPostingRepository", ledgerPostingRepository);

        dailySettlementService = new DailySettlementServiceImpl(
                paymentRepository,
                settlementBatchRepository,
                ledgerJournalRepository,
                refundRequestRepository,
                merchantWalletService
        );
    }

    @Test
    @DisplayName("Wallet blocks direct setBalance and only mutates balance via balanced LedgerJournal")
    void walletBlocksDirectSetBalanceAndAppliesBalancedJournalOnly() {
        Wallet wallet = Wallet.builder()
                .merchantId(10L)
                .balance(new BigDecimal("100000.00"))
                .build();

        assertThrows(UnsupportedOperationException.class,
                () -> wallet.setBalance(new BigDecimal("999999.00")),
                "Direct balance overwrite must be blocked by Wallet domain invariant");

        LedgerJournal unbalanced = LedgerJournal.builder()
                .journalKey("UNBALANCED-1")
                .journalType("TEST")
                .merchantId(10L)
                .build();
        unbalanced.addPosting(LedgerAccountCode.GATEWAY_CLEARING, 10L, PostingDirection.DEBIT, new BigDecimal("100000.00"));
        unbalanced.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, 10L, PostingDirection.CREDIT, new BigDecimal("80000.00"));

        assertThrows(com.smartparking.common.exception.AppException.class, unbalanced::validateAndSeal,
                "Unbalanced journal (debit != credit) must be rejected");
        assertThrows(com.smartparking.common.exception.AppException.class, () -> wallet.applyJournal(unbalanced));
    }

    @Test
    @DisplayName("Full 6-account double-entry lifecycle keeps totalDebit == totalCredit and reconciles Wallet.balance")
    void fullSixAccountLifecycleBalancesAndReconcilesWallet() {
        Long merchantId = 200L;
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(BigDecimal.ZERO.setScale(2))
                .build();

        lenient().when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));
        lenient().when(walletRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(wallet));
        lenient().when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(ledgerJournalRepository.save(any(LedgerJournal.class))).thenAnswer(inv -> {
            LedgerJournal j = inv.getArgument(0);
            if (j.getId() == null) {
                ReflectionTestUtils.setField(j, "id", 900L);
            }
            return j;
        });

        // 1. Escrow deposit of 100,000 VND (DEBIT GATEWAY_CLEARING, CREDIT CUSTOMER_DEPOSIT_ESCROW)
        Payment payment = Payment.builder()
                .id(501L)
                .bookingId(301L)
                .merchantId(merchantId)
                .amount(new BigDecimal("100000.00"))
                .build();
        when(ledgerJournalRepository.existsByJournalKey("ESCROW:PAYMENT:501")).thenReturn(false, true);

        merchantWalletService.recordPaymentEscrow(payment, "Escrow booking #301");
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()),
                "Escrow deposit must not prematurely inflate MERCHANT_PAYABLE before settlement");

        // 2. Settlement credit with 10% platform fee (DEBIT CUSTOMER_DEPOSIT_ESCROW 100k, CREDIT MERCHANT_PAYABLE 90k, CREDIT PLATFORM_FEE_REVENUE 10k)
        when(ledgerJournalRepository.existsByJournalKey("SETTLE:PAYMENT:501")).thenReturn(false);
        SettlementSplit split = merchantWalletService.creditSettlement(
                merchantId, 301L, payment, new BigDecimal("100000.00"), "Settle booking #301");
        assertEquals(0, new BigDecimal("90000.00").compareTo(split.getNetAmount()));
        assertEquals(0, new BigDecimal("10000.00").compareTo(split.getFeeAmount()));
        assertEquals(0, new BigDecimal("90000.00").compareTo(wallet.getBalance()));

        // 3. Withdrawal hold of 50,000 VND (DEBIT MERCHANT_PAYABLE, CREDIT MERCHANT_WITHDRAWAL_HOLD)
        merchantWalletService.holdWithdrawal(merchantId, new BigDecimal("50000.00"), "Hold withdrawal");
        assertEquals(0, new BigDecimal("40000.00").compareTo(wallet.getBalance()));

        // 4. Withdrawal rejection/refund of 50,000 VND (DEBIT MERCHANT_WITHDRAWAL_HOLD, CREDIT MERCHANT_PAYABLE)
        merchantWalletService.refundWithdrawal(merchantId, new BigDecimal("50000.00"), "Refund rejected withdrawal");
        assertEquals(0, new BigDecimal("90000.00").compareTo(wallet.getBalance()));

        // 5. Hold again & approve withdrawal (DEBIT MERCHANT_WITHDRAWAL_HOLD, CREDIT GATEWAY_CLEARING)
        merchantWalletService.holdWithdrawal(merchantId, new BigDecimal("50000.00"), "Hold withdrawal #2");
        merchantWalletService.settleApprovedWithdrawal(merchantId, 77L, new BigDecimal("50000.00"), "Approved withdrawal #77");
        assertEquals(0, new BigDecimal("40000.00").compareTo(wallet.getBalance()));

        // 6. Refund liability & completion on unsettled deposit (CUSTOMER_DEPOSIT_ESCROW -> REFUND_PAYABLE -> GATEWAY_CLEARING)
        merchantWalletService.recordRefundLiability(
                merchantId, 302L, 502L, new BigDecimal("25000.00"), "REFUND_REQ:BOOKING:302", "50% refund");
        merchantWalletService.settleCompletedRefund(
                merchantId, 302L, 502L, new BigDecimal("25000.00"), "REFUND_DONE:BOOKING:302", "Manual transfer verified");

        ArgumentCaptor<LedgerJournal> journalCaptor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(ledgerJournalRepository, atLeast(6)).save(journalCaptor.capture());
        for (LedgerJournal savedJournal : journalCaptor.getAllValues()) {
            assertEquals(0, savedJournal.getTotalDebit().compareTo(savedJournal.getTotalCredit()),
                    "Every saved journal must have totalDebit == totalCredit: " + savedJournal.getJournalKey());
        }
    }

    @Test
    @DisplayName("100% and 50% refunds BEFORE and AFTER settlement produce identical wallet balance and balanced ledger postings")
    void refundBeforeAndAfterSettlementReconcilesWalletAndLedger() {
        Long merchantId = 400L;
        List<LedgerJournal> recordedJournals = new ArrayList<>();
        java.util.Set<String> savedJournalKeys = new java.util.HashSet<>();

        when(ledgerJournalRepository.save(any(LedgerJournal.class))).thenAnswer(inv -> {
            LedgerJournal j = inv.getArgument(0);
            recordedJournals.add(j);
            savedJournalKeys.add(j.getJournalKey());
            return j;
        });
        when(ledgerJournalRepository.existsByJournalKey(anyString()))
                .thenAnswer(inv -> savedJournalKeys.contains(inv.getArgument(0)));
        lenient().when(ledgerJournalRepository.findByJournalKey(anyString()))
                .thenAnswer(inv -> recordedJournals.stream()
                        .filter(j -> j.getJournalKey().equals(inv.getArgument(0)))
                        .findFirst());

        // --- Scenario A: 100% and 50% refund BEFORE settlement ---
        Wallet walletBefore = Wallet.builder().merchantId(merchantId).balance(BigDecimal.ZERO.setScale(2)).build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(walletBefore));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment pFullRefundBefore = Payment.builder()
                .id(601L).bookingId(701L).merchantId(merchantId)
                .amount(new BigDecimal("100000.00")).status(PaymentStatusEnum.SUCCESS).build();
        Payment pHalfRefundBefore = Payment.builder()
                .id(602L).bookingId(702L).merchantId(merchantId)
                .amount(new BigDecimal("100000.00")).status(PaymentStatusEnum.SUCCESS).build();

        // Escrow both payments
        merchantWalletService.recordPaymentEscrow(pFullRefundBefore, "Escrow #601");
        merchantWalletService.recordPaymentEscrow(pHalfRefundBefore, "Escrow #602");
        assertEquals(0, BigDecimal.ZERO.compareTo(walletBefore.getBalance()));

        // Record 100% refund on #601 and 50% refund on #602 BEFORE settlement
        merchantWalletService.recordRefundLiability(merchantId, 701L, 601L, new BigDecimal("100000.00"), "REFUND_REQ:BOOKING:701", "100% pre-settle");
        merchantWalletService.recordRefundLiability(merchantId, 702L, 602L, new BigDecimal("50000.00"), "REFUND_REQ:BOOKING:702", "50% pre-settle");
        assertEquals(0, BigDecimal.ZERO.compareTo(walletBefore.getBalance()),
                "Pre-settlement refund debits CUSTOMER_DEPOSIT_ESCROW without touching MERCHANT_PAYABLE");

        // Run daily settlement after pre-settlement refunds: #601 has 0 settleable, #602 has 50,000 settleable -> 45,000 net to merchant
        LocalDate dateBefore = LocalDate.of(2026, 11, 8);
        when(paymentRepository.findSuccessfulPaymentsInWindow(dateBefore.atStartOfDay(), dateBefore.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(pFullRefundBefore, pHalfRefundBefore));
        when(refundRequestRepository.findByBookingId(701L)).thenReturn(Optional.of(
                RefundRequest.builder().bookingId(701L).refundAmount(new BigDecimal("100000.00")).status("REQUESTED").build()));
        when(refundRequestRepository.findByBookingId(702L)).thenReturn(Optional.of(
                RefundRequest.builder().bookingId(702L).refundAmount(new BigDecimal("50000.00")).status("REQUESTED").build()));
        when(settlementBatchRepository.findByBusinessDateAndMerchantId(dateBefore, merchantId)).thenReturn(Optional.empty());
        when(settlementBatchRepository.saveAndFlush(any(SettlementBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        dailySettlementService.runDailySettlement(dateBefore);
        assertEquals(0, new BigDecimal("45000.00").compareTo(walletBefore.getBalance()),
                "After 100% refund on #601 and 50% refund on #602 before settlement, merchant net balance must be 45,000 VND");

        // --- Scenario B: 100% and 50% refund AFTER settlement ---
        Wallet walletAfter = Wallet.builder().merchantId(merchantId).balance(BigDecimal.ZERO.setScale(2)).build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(walletAfter));

        Payment pFullRefundAfter = Payment.builder()
                .id(603L).bookingId(703L).merchantId(merchantId)
                .amount(new BigDecimal("100000.00")).status(PaymentStatusEnum.SUCCESS).build();
        Payment pHalfRefundAfter = Payment.builder()
                .id(604L).bookingId(704L).merchantId(merchantId)
                .amount(new BigDecimal("100000.00")).status(PaymentStatusEnum.SUCCESS).build();

        merchantWalletService.recordPaymentEscrow(pFullRefundAfter, "Escrow #603");
        merchantWalletService.recordPaymentEscrow(pHalfRefundAfter, "Escrow #604");

        // Both payments settle first (90,000 + 90,000 = 180,000 net to merchant)
        merchantWalletService.creditSettlement(merchantId, 703L, pFullRefundAfter, new BigDecimal("100000.00"), "Settle #603");
        merchantWalletService.creditSettlement(merchantId, 704L, pHalfRefundAfter, new BigDecimal("100000.00"), "Settle #604");
        assertEquals(0, new BigDecimal("180000.00").compareTo(walletAfter.getBalance()));

        // Duplicate creditSettlement for #603 is a no-op
        merchantWalletService.creditSettlement(merchantId, 703L, pFullRefundAfter, new BigDecimal("100000.00"), "Duplicate settle #603");
        assertEquals(0, new BigDecimal("180000.00").compareTo(walletAfter.getBalance()));

        // Now 100% refund on #603 (-90,000 merchant payable, -10,000 fee) and 50% refund on #604 (-45,000 merchant payable, -5,000 fee) AFTER settlement
        merchantWalletService.recordRefundLiability(merchantId, 703L, 603L, new BigDecimal("100000.00"), "REFUND_REQ:BOOKING:703", "100% post-settle");
        assertEquals(0, new BigDecimal("90000.00").compareTo(walletAfter.getBalance()));

        merchantWalletService.recordRefundLiability(merchantId, 704L, 604L, new BigDecimal("50000.00"), "REFUND_REQ:BOOKING:704", "50% post-settle");
        assertEquals(0, new BigDecimal("45000.00").compareTo(walletAfter.getBalance()),
                "Post-settlement refunds must leave the exact same 45,000 VND merchant balance as pre-settlement refunds");

        for (LedgerJournal j : recordedJournals) {
            assertEquals(0, j.getTotalDebit().compareTo(j.getTotalCredit()),
                    "Every journal must balance: " + j.getJournalKey());
        }
    }

    @Test
    @DisplayName("reverseJournal creates an inverse journal and reverses MERCHANT_PAYABLE projection on Wallet")
    void reverseJournalCreatesInversePostingsAndUpdatesWallet() {
        Long merchantId = 300L;
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(new BigDecimal("90000.00"))
                .build();

        LedgerJournal original = LedgerJournal.builder()
                .id(15L)
                .journalKey("SETTLE:PAYMENT:888")
                .journalType("SETTLEMENT_CREDIT")
                .merchantId(merchantId)
                .bookingId(88L)
                .paymentId(888L)
                .currency("VND")
                .feePercent(new BigDecimal("10.00"))
                .build();
        original.addPosting(LedgerAccountCode.CUSTOMER_DEPOSIT_ESCROW, merchantId, PostingDirection.DEBIT, new BigDecimal("100000.00"));
        original.addPosting(LedgerAccountCode.MERCHANT_PAYABLE, merchantId, PostingDirection.CREDIT, new BigDecimal("90000.00"));
        original.addPosting(LedgerAccountCode.PLATFORM_FEE_REVENUE, merchantId, PostingDirection.CREDIT, new BigDecimal("10000.00"));
        original.validateAndSeal();

        when(ledgerJournalRepository.findById(15L)).thenReturn(Optional.of(original));
        when(ledgerJournalRepository.findByJournalKey("REV:SETTLE:PAYMENT:888")).thenReturn(Optional.empty());
        when(ledgerJournalRepository.save(any(LedgerJournal.class))).thenAnswer(inv -> inv.getArgument(0));
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

        LedgerJournal reversal = merchantWalletService.reverseJournal(15L, "Dispute refund reversal");

        assertEquals("REVERSAL", reversal.getJournalType());
        assertEquals(15L, reversal.getReversalOfJournalId());
        assertEquals(0, new BigDecimal("100000.00").compareTo(reversal.getTotalDebit()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(reversal.getTotalCredit()));
        assertEquals(0, BigDecimal.ZERO.compareTo(wallet.getBalance()),
                "Wallet balance must return to 0 after reversing 90,000 VND credit");
    }

    @Test
    @DisplayName("DailySettlementService settles prior Asia/Ho_Chi_Minh day at 10% fee, deducts active refunds, and is idempotent on duplicate/concurrent runs")
    void dailySettlementAppliesTenPercentFeeAndIsIdempotent() {
        LocalDate businessDate = LocalDate.of(2026, 11, 9);
        Long merchantId = 50L;

        Payment p1 = Payment.builder()
                .id(1001L)
                .bookingId(2001L)
                .merchantId(merchantId)
                .amount(new BigDecimal("100000.00"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        Payment p2WithPartialRefund = Payment.builder()
                .id(1002L)
                .bookingId(2002L)
                .merchantId(merchantId)
                .amount(new BigDecimal("80000.00"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();

        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(BigDecimal.ZERO.setScale(2))
                .build();

        when(paymentRepository.findSuccessfulPaymentsInWindow(
                businessDate.atStartOfDay(), businessDate.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(p1, p2WithPartialRefund));

        when(refundRequestRepository.findByBookingId(2001L)).thenReturn(Optional.empty());
        when(refundRequestRepository.findByBookingId(2002L)).thenReturn(Optional.of(
                RefundRequest.builder()
                        .bookingId(2002L)
                        .refundAmount(new BigDecimal("40000.00"))
                        .status("REQUESTED")
                        .build()
        ));

        when(settlementBatchRepository.findByBusinessDateAndMerchantId(businessDate, merchantId))
                .thenReturn(Optional.empty());
        when(ledgerJournalRepository.findByJournalKey(anyString())).thenReturn(Optional.empty());
        when(ledgerJournalRepository.existsByJournalKey(anyString())).thenReturn(false);
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(settlementBatchRepository.saveAndFlush(any(SettlementBatch.class))).thenAnswer(inv -> inv.getArgument(0));

        // First run: settles 100,000 + (80,000 - 40,000) = 140,000 gross -> 14,000 fee (10%) -> 126,000 net
        List<SettlementBatch> firstRun = dailySettlementService.runDailySettlement(businessDate);
        assertEquals(1, firstRun.size());
        SettlementBatch batch = firstRun.get(0);
        assertEquals(0, new BigDecimal("140000.00").compareTo(batch.getGrossAmount()));
        assertEquals(0, new BigDecimal("14000.00").compareTo(batch.getFeeAmount()));
        assertEquals(0, new BigDecimal("126000.00").compareTo(batch.getNetAmount()));
        assertEquals(0, new BigDecimal("126000.00").compareTo(wallet.getBalance()));

        // Second run (duplicate invocation): returns existing COMPLETED batch without double-crediting wallet
        when(settlementBatchRepository.findByBusinessDateAndMerchantId(businessDate, merchantId))
                .thenReturn(Optional.of(batch));
        List<SettlementBatch> secondRun = dailySettlementService.runDailySettlement(businessDate);
        assertEquals(1, secondRun.size());
        assertEquals(0, new BigDecimal("126000.00").compareTo(wallet.getBalance()),
                "Duplicate settlement run must not double-credit Merchant wallet");

        // Concurrent race condition on saveAndFlush: handles DataIntegrityViolationException gracefully
        SettlementBatch pendingBatch = SettlementBatch.builder()
                .batchKey("SETTLEMENT:2026-11-09:MERCHANT:50")
                .businessDate(businessDate)
                .merchantId(merchantId)
                .status("PENDING")
                .build();
        when(settlementBatchRepository.findByBusinessDateAndMerchantId(businessDate, merchantId))
                .thenReturn(Optional.of(pendingBatch))
                .thenReturn(Optional.of(batch));
        when(settlementBatchRepository.saveAndFlush(any(SettlementBatch.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate uk_settlement_date_merchant"));

        List<SettlementBatch> concurrentRun = dailySettlementService.runDailySettlement(businessDate);
        assertEquals(1, concurrentRun.size());
        assertEquals("COMPLETED", concurrentRun.get(0).getStatus());
    }
}
