package com.smartparking.payment.application.service;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.enums.PaymentTypeEnum;
import com.smartparking.common.enums.WalletLedgerEntryTypeEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.impl.MerchantWalletServiceImpl;
import com.smartparking.payment.domain.model.CashCheckoutReceipt;
import com.smartparking.payment.domain.model.LedgerJournal;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.RefundRequest;
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.model.WalletLedgerEntry;
import com.smartparking.payment.domain.repository.CashCheckoutReceiptRepository;
import com.smartparking.payment.domain.repository.LedgerJournalRepository;
import com.smartparking.payment.domain.repository.PaymentRepository;
import com.smartparking.payment.domain.repository.RefundRequestRepository;
import com.smartparking.payment.domain.repository.WalletLedgerEntryRepository;
import com.smartparking.payment.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantWalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerEntryRepository ledgerRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CashCheckoutReceiptRepository cashCheckoutReceiptRepository;

    @Mock
    private RefundRequestRepository refundRequestRepository;

    @Mock
    private LedgerJournalRepository ledgerJournalRepository;

    @InjectMocks
    private MerchantWalletServiceImpl merchantWalletService;

    private final Long merchantId = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(merchantWalletService, "platformFeePercent", new BigDecimal("10.0"));
        ReflectionTestUtils.setField(merchantWalletService, "cashCheckoutReceiptRepository", cashCheckoutReceiptRepository);
        ReflectionTestUtils.setField(merchantWalletService, "refundRequestRepository", refundRequestRepository);
    }

    @Test
    @DisplayName("Split calculation divides 10% platform fee and 90% merchant net")
    void testSplitCalculation() {
        BigDecimal gross = new BigDecimal("100000");
        SettlementSplit split = merchantWalletService.split(gross);

        assertNotNull(split);
        assertEquals(new BigDecimal("10000.00"), split.getFeeAmount());
        assertEquals(new BigDecimal("90000.00"), split.getNetAmount());
        assertEquals(gross, split.getGrossAmount());

        // Zero or null gross returns zero split
        SettlementSplit zeroSplit = merchantWalletService.split(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, zeroSplit.getNetAmount());
    }

    @Test
    @DisplayName("Credit settlement updates merchant wallet balance and saves ledger")
    void testCreditSettlement() {
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(new BigDecimal("50000"))
                .build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));

        SettlementSplit split = merchantWalletService.creditSettlement(
                merchantId, 1L, null, new BigDecimal("100000"), "Booking payout"
        );

        assertNotNull(split);
        assertEquals(new BigDecimal("140000.00"), wallet.getBalance());
        verify(walletRepository, times(1)).save(wallet);
        verify(ledgerRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Hold withdrawal throws BAD_REQUEST when balance is insufficient")
    void testHoldWithdrawalInsufficientBalance() {
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(new BigDecimal("20000"))
                .build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));

        AppException ex = assertThrows(AppException.class, () ->
                merchantWalletService.holdWithdrawal(merchantId, new BigDecimal("50000"), "Withdrawal hold"));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Số dư khả dụng không đủ"));
    }

    @Test
    @DisplayName("Hold withdrawal successfully reduces merchant balance")
    void testHoldWithdrawalSuccess() {
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(new BigDecimal("100000"))
                .build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));

        merchantWalletService.holdWithdrawal(merchantId, new BigDecimal("40000"), "Withdrawal hold");

        assertEquals(new BigDecimal("60000"), wallet.getBalance());
        verify(walletRepository, times(1)).save(wallet);
        verify(ledgerRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Refund withdrawal successfully restores merchant balance")
    void testRefundWithdrawal() {
        Wallet wallet = Wallet.builder()
                .merchantId(merchantId)
                .balance(new BigDecimal("50000"))
                .build();
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));

        merchantWalletService.refundWithdrawal(merchantId, new BigDecimal("30000"), "Admin rejected");

        assertEquals(new BigDecimal("80000"), wallet.getBalance());
        verify(walletRepository, times(1)).save(wallet);
        verify(ledgerRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Get wallet balance returns balance or zero when not found")
    void testGetWalletBalance() {
        when(walletRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(Wallet.builder().merchantId(merchantId).balance(new BigDecimal("75000")).build()));
        assertEquals(new BigDecimal("75000"), merchantWalletService.getWalletBalance(merchantId));

        when(walletRepository.findByMerchantId(999L)).thenReturn(Optional.empty());
        assertEquals(BigDecimal.ZERO, merchantWalletService.getWalletBalance(999L));
    }

    @Test
    @DisplayName("Get revenue summary calculates ledger totals and continuous 7 days series")
    void testGetRevenueSummary() {
        LocalDateTime now = LocalDateTime.now();
        Payment p = Payment.builder()
                .id(1L).merchantId(merchantId).parkingLotId(10L).bookingId(100L)
                .amount(new BigDecimal("200000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        p.setCreatedAt(now);
        WalletLedgerEntry settled = WalletLedgerEntry.builder()
                .id(10L).merchantId(merchantId).bookingId(100L).payment(p)
                .entryType(WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT)
                .grossAmount(new BigDecimal("200000")).feePercent(new BigDecimal("10"))
                .feeAmount(new BigDecimal("20000")).netAmount(new BigDecimal("180000"))
                .createdAt(now).build();

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(List.of(settled));
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(p));
        when(walletRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(Wallet.builder().balance(new BigDecimal("180000")).build()));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        assertNotNull(summary);
        assertEquals(new BigDecimal("200000"), summary.getTotalGrossRevenue());
        assertEquals(new BigDecimal("20000"), summary.getTotalPlatformFee());
        assertEquals(new BigDecimal("180000"), summary.getTotalNetRevenue());
        assertEquals(new BigDecimal("180000"), summary.getCurrentWalletBalance());
        assertNotNull(summary.getSeries());
        assertEquals(7, summary.getSeries().size());
    }

    @Test
    @DisplayName("Get revenue summary aggregates daily settled ledger revenue and booking counts")
    void testGetRevenueSummaryWithPayments() {
        LocalDateTime todayNoon = LocalDate.now().atTime(12, 0);
        LocalDateTime yesterdayNoon = LocalDate.now().minusDays(1).atTime(14, 0);

        Payment p1 = Payment.builder()
                .id(1L).merchantId(merchantId).parkingLotId(10L).bookingId(101L)
                .amount(new BigDecimal("100000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        p1.setCreatedAt(todayNoon);
        Payment p2 = Payment.builder()
                .id(2L).merchantId(merchantId).parkingLotId(10L).bookingId(102L)
                .amount(new BigDecimal("50000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        p2.setCreatedAt(todayNoon);
        Payment p3 = Payment.builder()
                .id(3L).merchantId(merchantId).parkingLotId(10L).bookingId(103L)
                .amount(new BigDecimal("200000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        p3.setCreatedAt(yesterdayNoon);

        List<WalletLedgerEntry> entries = List.of(
                settledEntry(1L, p3, "200000", "20000", "180000", yesterdayNoon),
                settledEntry(2L, p1, "100000", "10000", "90000", todayNoon),
                settledEntry(3L, p2, "50000", "5000", "45000", todayNoon)
        );
        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(entries);
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(p3, p1, p2));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        assertNotNull(summary);
        assertEquals(7, summary.getSeries().size());
        assertEquals(3L, summary.getCompletedBookings());

        String todayKey = LocalDate.now().toString();
        String yesterdayKey = LocalDate.now().minusDays(1).toString();

        var todayPoint = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(todayKey))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("135000").compareTo(todayPoint.getRevenue()));
        assertEquals(2L, todayPoint.getBookingCount());

        var yesterdayPoint = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(yesterdayKey))
                .findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("180000").compareTo(yesterdayPoint.getRevenue()));
        assertEquals(1L, yesterdayPoint.getBookingCount());
    }

    @Test
    @DisplayName("Get revenue summary filters by parking lot id when provided")
    void testGetRevenueSummaryWithParkingLotFilter() {
        Long parkingLotId = 99L;
        LocalDateTime now = LocalDateTime.now();
        Payment p = Payment.builder()
                .id(1L).merchantId(merchantId).parkingLotId(parkingLotId).bookingId(201L)
                .amount(new BigDecimal("80000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        p.setCreatedAt(now);
        Payment otherLot = Payment.builder()
                .id(2L).merchantId(merchantId).parkingLotId(88L).bookingId(202L)
                .amount(new BigDecimal("50000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        otherLot.setCreatedAt(now);

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(List.of(
                settledEntry(1L, p, "80000", "8000", "72000", now),
                settledEntry(2L, otherLot, "50000", "5000", "45000", now)
        ));
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(p, otherLot));

        var summary = merchantWalletService.getRevenueSummary(merchantId, parkingLotId, null, null, "DAY");

        assertNotNull(summary);
        assertEquals(1L, summary.getCompletedBookings());
        assertEquals(0, new BigDecimal("80000").compareTo(summary.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("72000").compareTo(summary.getTotalNetRevenue()));
        assertEquals(2, summary.getRevenueByParkingLot().size());
    }

    @Test
    @DisplayName("Credit settlement creates new wallet if one does not exist yet")
    void testCreditSettlementCreatesNewWallet() {
        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.empty());
        Wallet newWallet = Wallet.builder().merchantId(merchantId).balance(BigDecimal.ZERO).build();
        when(walletRepository.save(any(Wallet.class))).thenReturn(newWallet);

        SettlementSplit split = merchantWalletService.creditSettlement(
                merchantId, 2L, null, new BigDecimal("50000"), "First settlement"
        );

        assertNotNull(split);
        verify(walletRepository, atLeastOnce()).save(any(Wallet.class));
    }

    @Test
    @DisplayName("Get revenue summary splits ONLINE_TRANSFER, COUNTER_QR, UNKNOWN, and CASH across all lots and per lot without altering wallet balance")
    void testGetRevenueSummaryThreeChannelsAllLotsAndFilteredLot() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayNoon = today.atTime(12, 0);

        Payment pOnline = Payment.builder().id(1L).merchantId(merchantId).parkingLotId(10L).bookingId(501L)
                .amount(new BigDecimal("100000")).paymentChannel("ONLINE_TRANSFER").status(PaymentStatusEnum.SUCCESS).build();
        pOnline.setCreatedAt(todayNoon);
        Payment pQr = Payment.builder().id(2L).merchantId(merchantId).parkingLotId(10L).bookingId(502L)
                .amount(new BigDecimal("50000")).paymentChannel("COUNTER_QR").status(PaymentStatusEnum.SUCCESS).build();
        pQr.setCreatedAt(todayNoon);
        Payment pLot20Legacy = Payment.builder().id(3L).merchantId(merchantId).parkingLotId(20L).bookingId(503L)
                .amount(new BigDecimal("200000")).paymentChannel("UNKNOWN").status(PaymentStatusEnum.SUCCESS).build();
        pLot20Legacy.setCreatedAt(todayNoon);

        List<WalletLedgerEntry> entries = List.of(
                settledEntry(10L, pOnline, "100000", "10000", "90000", todayNoon),
                settledEntry(11L, pQr, "50000", "5000", "45000", todayNoon),
                settledEntry(12L, pLot20Legacy, "200000", "20000", "180000", todayNoon)
        );

        CashCheckoutReceiptRepository.LotCashAggregation lot10Cash = cashAgg(10L, new BigDecimal("40000"), 1L);
        CashCheckoutReceiptRepository.LotCashAggregation lot20Cash = cashAgg(20L, new BigDecimal("85000"), 2L);
        CashCheckoutReceipt cashSameBooking = CashCheckoutReceipt.builder().bookingId(501L).merchantId(merchantId)
                .parkingLotId(10L).amount(new BigDecimal("40000")).status("COMPLETED").updatedAt(todayNoon).build();

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(entries);
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(pOnline, pQr, pLot20Legacy));
        when(walletRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(Wallet.builder().merchantId(merchantId).balance(new BigDecimal("315000")).build()));
        when(cashCheckoutReceiptRepository.aggregateCompletedByMerchantGroupedByLot(merchantId))
                .thenReturn(List.of(lot10Cash, lot20Cash));
        when(cashCheckoutReceiptRepository.findByMerchantIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                eq(merchantId), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(cashSameBooking));

        var allLots = merchantWalletService.getRevenueSummary(merchantId, null, null, null, "DAY");

        assertEquals(0, new BigDecimal("350000").compareTo(allLots.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("315000").compareTo(allLots.getTotalNetRevenue()));
        assertEquals(0, new BigDecimal("315000").compareTo(allLots.getCurrentWalletBalance()));
        assertEquals(0, new BigDecimal("35000").compareTo(allLots.getTotalPlatformFee()));

        // Channels split cleanly including legacy UNKNOWN
        assertEquals(0, new BigDecimal("100000").compareTo(allLots.getOnlineTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("90000").compareTo(allLots.getOnlineTransferNetRevenue()));
        assertEquals(1L, allLots.getOnlineTransferCount());
        assertEquals(0, new BigDecimal("50000").compareTo(allLots.getCounterQrGrossRevenue()));
        assertEquals(0, new BigDecimal("45000").compareTo(allLots.getCounterQrNetRevenue()));
        assertEquals(1L, allLots.getCounterQrCount());
        assertEquals(0, new BigDecimal("200000").compareTo(allLots.getUnclassifiedTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("180000").compareTo(allLots.getUnclassifiedTransferNetRevenue()));
        assertEquals(1L, allLots.getUnclassifiedTransferCount());
        assertEquals(0, new BigDecimal("125000").compareTo(allLots.getTotalCashRevenue()));
        assertEquals(3L, allLots.getTotalCashBookings());

        assertEquals(2, allLots.getRevenueByParkingLot().size());
        var lot10Dto = allLots.getRevenueByParkingLot().get(0);
        assertEquals(10L, lot10Dto.getParkingLotId());
        assertEquals(0, new BigDecimal("90000").compareTo(lot10Dto.getOnlineTransferRevenue()));
        assertEquals(0, new BigDecimal("45000").compareTo(lot10Dto.getCounterQrRevenue()));
        assertEquals(0, new BigDecimal("40000").compareTo(lot10Dto.getCashRevenue()));

        // Filter to lot 10 only
        when(cashCheckoutReceiptRepository.findByMerchantIdAndParkingLotIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                eq(merchantId), eq(10L), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(cashSameBooking));

        var lot10Only = merchantWalletService.getRevenueSummary(merchantId, 10L, null, null, "DAY");
        assertEquals(0, new BigDecimal("90000").compareTo(lot10Only.getOnlineTransferNetRevenue()));
        assertEquals(0, new BigDecimal("45000").compareTo(lot10Only.getCounterQrNetRevenue()));
        assertEquals(0, new BigDecimal("40000").compareTo(lot10Only.getTotalCashRevenue()));
        assertEquals(2L, lot10Only.getCompletedBookings()); // 501 (deduplicated) + 502
    }

    @Test
    @DisplayName("Settled channel net matches ledger after partial refund and adjustment; pending settlement excludes refunded portion")
    void testPartialRefundSettlementAndAdjustmentReconcileWithLedger() {
        LocalDateTime todayNoon = LocalDate.now().atTime(12, 0);

        // Payment 1: 100,000 deposit still status=SUCCESS, 50,000 partially refunded -> settled at 10% on 50,000 (fee 5,000, net 45,000) + -5,000 adjustment
        Payment settledDeposit = Payment.builder()
                .id(11L).merchantId(merchantId).parkingLotId(10L).bookingId(701L)
                .amount(new BigDecimal("100000")).paymentType(PaymentTypeEnum.DEPOSIT)
                .paymentChannel("ONLINE_TRANSFER").status(PaymentStatusEnum.SUCCESS).build();
        settledDeposit.setCreatedAt(todayNoon);

        WalletLedgerEntry settlementEntry = settledEntry(101L, settledDeposit, "50000", "5000", "45000", todayNoon);
        WalletLedgerEntry adjustmentEntry = WalletLedgerEntry.builder()
                .id(102L).merchantId(merchantId).bookingId(701L).payment(settledDeposit)
                .entryType(WalletLedgerEntryTypeEnum.ADJUSTMENT)
                .grossAmount(BigDecimal.ZERO).feePercent(BigDecimal.ZERO)
                .feeAmount(BigDecimal.ZERO).netAmount(new BigDecimal("-5000"))
                .createdAt(todayNoon).build();

        // Payment 2: 100,000 deposit NOT YET settled, with 50,000 valid partial refund -> pendingSettlementGross = 50,000 (not 100,000)
        Payment unsettledDeposit = Payment.builder()
                .id(12L).merchantId(merchantId).parkingLotId(10L).bookingId(702L)
                .amount(new BigDecimal("100000")).paymentType(PaymentTypeEnum.DEPOSIT)
                .paymentChannel("ONLINE_TRANSFER").status(PaymentStatusEnum.SUCCESS).build();
        unsettledDeposit.setCreatedAt(todayNoon);

        RefundRequest partialRefund = RefundRequest.builder()
                .id(1L).bookingId(702L).paymentId(12L).merchantId(merchantId)
                .refundAmount(new BigDecimal("50000")).status("COMPLETED").build();

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId))
                .thenReturn(List.of(settlementEntry, adjustmentEntry));
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(settledDeposit, unsettledDeposit));
        when(refundRequestRepository.findByBookingIdIn(any()))
                .thenReturn(List.of(partialRefund));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        // Contract cumulative gross includes all SUCCESS payments (50k settled + 50k pending = 100k)
        assertEquals(0, new BigDecimal("100000").compareTo(summary.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("50000").compareTo(summary.getTotalSettledGrossRevenue()));
        assertEquals(0, new BigDecimal("5000").compareTo(summary.getTotalPlatformFee()));
        assertEquals(0, new BigDecimal("40000").compareTo(summary.getTotalNetRevenue()));
        assertEquals(0, new BigDecimal("40000").compareTo(summary.getOnlineTransferNetRevenue()));
        assertEquals(1L, summary.getOnlineTransferCount());

        // Pre-settlement escrow tracks refund-adjusted gross separately
        assertEquals(0, new BigDecimal("50000").compareTo(summary.getPendingSettlementGrossRevenue()));
        assertEquals(0, new BigDecimal("50000").compareTo(summary.getRevenueByParkingLot().get(0).getPendingSettlementGrossRevenue()));
    }

    @Test
    @DisplayName("Overnight booking with deposit on Day 1 and cash checkout on Day 2 is deduplicated to 1 booking across the period")
    void testOvernightDepositAndNextDayCashDeduplicatedAcrossPeriod() {
        LocalDate day1 = LocalDate.now().minusDays(1);
        LocalDate day2 = LocalDate.now();
        LocalDateTime day1Evening = day1.atTime(22, 0);
        LocalDateTime day2Morning = day2.atTime(8, 0);

        Payment day1Deposit = Payment.builder()
                .id(21L).merchantId(merchantId).parkingLotId(10L).bookingId(801L)
                .amount(new BigDecimal("50000")).paymentChannel("ONLINE_TRANSFER")
                .status(PaymentStatusEnum.SUCCESS).build();
        day1Deposit.setCreatedAt(day1Evening);

        WalletLedgerEntry day1Ledger = settledEntry(201L, day1Deposit, "50000", "5000", "45000", day1Evening);

        CashCheckoutReceipt day2Cash = CashCheckoutReceipt.builder()
                .bookingId(801L).merchantId(merchantId).parkingLotId(10L)
                .amount(new BigDecimal("120000")).status("COMPLETED").updatedAt(day2Morning).build();

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(List.of(day1Ledger));
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(day1Deposit));
        when(cashCheckoutReceiptRepository.aggregateCompletedByMerchantGroupedByLot(merchantId))
                .thenReturn(List.of(cashAgg(10L, new BigDecimal("120000"), 1L)));
        when(cashCheckoutReceiptRepository.findByMerchantIdAndStatusAndUpdatedAtBetweenOrderByUpdatedAtAsc(
                eq(merchantId), eq("COMPLETED"), any(), any()))
                .thenReturn(List.of(day2Cash));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        var day1Point = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(day1.toString())).findFirst().orElseThrow();
        var day2Point = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(day2.toString())).findFirst().orElseThrow();

        assertEquals(1L, day1Point.getBookingCount());
        assertEquals(1L, day2Point.getBookingCount());
        // Period-wide deduplication ensures booking 801 is NOT double-counted as 2!
        assertEquals(1L, summary.getCompletedBookings());
    }

    @Test
    @DisplayName("End-to-end creditSettlement -> post-settlement recordRefundLiability -> getRevenueSummary reduces gross, fee, and net across all-time, period, and per-lot")
    void testPostSettlementRefundAdjustmentReducesGrossFeeAndNetAcrossAllScopes() {
        ReflectionTestUtils.setField(merchantWalletService, "ledgerJournalRepository", ledgerJournalRepository);

        LocalDate today = LocalDate.now();
        LocalDateTime settledAt = today.atTime(10, 0);
        Payment payment = Payment.builder()
                .id(901L).merchantId(merchantId).parkingLotId(10L).bookingId(9001L)
                .amount(new BigDecimal("100000.00")).paymentType(PaymentTypeEnum.DEPOSIT)
                .paymentChannel("ONLINE_TRANSFER").status(PaymentStatusEnum.SUCCESS).build();
        payment.setCreatedAt(settledAt);

        Wallet wallet = Wallet.builder().merchantId(merchantId).balance(BigDecimal.ZERO).build();
        java.util.List<WalletLedgerEntry> savedLedgerEntries = new java.util.ArrayList<>();
        java.util.Map<String, LedgerJournal> savedJournals = new java.util.HashMap<>();

        when(walletRepository.findByMerchantIdForUpdate(merchantId)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByMerchantId(merchantId)).thenReturn(Optional.of(wallet));
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.getReferenceById(901L)).thenReturn(payment);
        when(ledgerRepository.save(any(WalletLedgerEntry.class))).thenAnswer(inv -> {
            WalletLedgerEntry entry = inv.getArgument(0);
            if (entry.getCreatedAt() == null) {
                entry.setCreatedAt(settledAt);
            }
            savedLedgerEntries.add(entry);
            return entry;
        });
        when(ledgerJournalRepository.existsByJournalKey(anyString())).thenAnswer(inv -> savedJournals.containsKey(inv.getArgument(0)));
        when(ledgerJournalRepository.findByJournalKey(anyString())).thenAnswer(inv -> Optional.ofNullable(savedJournals.get(inv.getArgument(0))));
        when(ledgerJournalRepository.findByPaymentId(901L)).thenReturn(List.of());
        when(ledgerJournalRepository.save(any(LedgerJournal.class))).thenAnswer(inv -> {
            LedgerJournal j = inv.getArgument(0);
            savedJournals.put(j.getJournalKey(), j);
            return j;
        });

        // Step 1: Settle 100,000 VND (10% fee -> fee 10,000, net 90,000)
        merchantWalletService.creditSettlement(merchantId, 9001L, payment, new BigDecimal("100000"), "Settle deposit");

        // Step 2: Post-settlement partial refund of 50,000 VND (creates ADJUSTMENT with gross=+50,000, fee=-5,000, net=-45,000)
        merchantWalletService.recordRefundLiability(merchantId, 9001L, 901L, new BigDecimal("50000"), "REFUND_REQ:9001", "Partial refund after settlement");

        assertEquals(2, savedLedgerEntries.size());
        WalletLedgerEntry refundAdjustment = savedLedgerEntries.get(1);
        assertEquals(WalletLedgerEntryTypeEnum.ADJUSTMENT, refundAdjustment.getEntryType());
        assertEquals(0, new BigDecimal("50000.00").compareTo(refundAdjustment.getGrossAmount()));
        assertEquals(0, new BigDecimal("-5000.00").compareTo(refundAdjustment.getFeeAmount()));
        assertEquals(0, new BigDecimal("-45000.00").compareTo(refundAdjustment.getNetAmount()));

        // Step 3: Revenue summary over explicit date range covering today
        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(savedLedgerEntries);
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(payment));

        String todayStr = today.toString();
        var summary = merchantWalletService.getRevenueSummary(merchantId, null, todayStr, todayStr, "DAY");

        // All-time totals: Gross must be 50,000 (NOT 150,000!), Fee = 5,000, Net = 45,000
        assertEquals(0, new BigDecimal("50000.00").compareTo(summary.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(summary.getTotalPlatformFee()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(summary.getTotalNetRevenue()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(summary.getOnlineTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(summary.getOnlineTransferNetRevenue()));

        // Period totals must also be 50,000 / 5,000 / 45,000
        assertEquals(0, new BigDecimal("50000.00").compareTo(summary.getPeriodTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(summary.getPeriodTotalPlatformFee()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(summary.getPeriodTotalNetRevenue()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(summary.getPeriodOnlineTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(summary.getPeriodOnlineTransferNetRevenue()));

        // Per-lot breakdown must match for both all-time and period
        assertEquals(1, summary.getRevenueByParkingLot().size());
        var lot10 = summary.getRevenueByParkingLot().get(0);
        assertEquals(0, new BigDecimal("50000.00").compareTo(lot10.getOnlineTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(lot10.getOnlineTransferRevenue()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(lot10.getPeriodOnlineTransferGrossRevenue()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(lot10.getPeriodOnlineTransferRevenue()));

        // Filtered directly to lot 10 must also report 50,000 / 5,000 / 45,000 for both all-time and period
        var lot10Filtered = merchantWalletService.getRevenueSummary(merchantId, 10L, todayStr, todayStr, "DAY");
        assertEquals(0, new BigDecimal("50000.00").compareTo(lot10Filtered.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(lot10Filtered.getTotalPlatformFee()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(lot10Filtered.getTotalNetRevenue()));
        assertEquals(0, new BigDecimal("50000.00").compareTo(lot10Filtered.getPeriodTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("5000.00").compareTo(lot10Filtered.getPeriodTotalPlatformFee()));
        assertEquals(0, new BigDecimal("45000.00").compareTo(lot10Filtered.getPeriodTotalNetRevenue()));
    }

    @Test
    @DisplayName("F-01: Unsettled SUCCESS payment made today is included in totalGrossRevenue, today's series point, and completedBookings")
    void testUnsettledPaymentIncludedInRealtimeSeriesAndCumulativeGross() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayMorning = today.atTime(10, 30);

        Payment unsettled = Payment.builder()
                .id(999L).merchantId(merchantId).parkingLotId(10L).bookingId(9991L)
                .amount(new BigDecimal("120000")).paymentType(PaymentTypeEnum.DEPOSIT)
                .paymentChannel("ONLINE_TRANSFER").status(PaymentStatusEnum.SUCCESS).build();
        unsettled.setCreatedAt(todayMorning);

        when(ledgerRepository.findRevenueEntriesWithPaymentByMerchantId(merchantId)).thenReturn(List.of());
        when(paymentRepository.findByMerchantIdAndStatusOrderByCreatedAtAsc(merchantId, PaymentStatusEnum.SUCCESS))
                .thenReturn(List.of(unsettled));
        when(walletRepository.findByMerchantId(merchantId))
                .thenReturn(Optional.of(Wallet.builder().merchantId(merchantId).balance(BigDecimal.ZERO).build()));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        // Cumulative gross includes the unsettled payment
        assertEquals(0, new BigDecimal("120000").compareTo(summary.getTotalGrossRevenue()));
        assertEquals(0, new BigDecimal("120000").compareTo(summary.getPendingSettlementGrossRevenue()));
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalSettledGrossRevenue()));
        assertEquals(1L, summary.getCompletedBookings());

        // Today's series point contains the unsettled payment
        String todayKey = today.toString();
        var todayPoint = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(todayKey))
                .findFirst().orElseThrow();
        // 120,000 * 90% = 108,000.00 net
        assertEquals(0, new BigDecimal("108000.00").compareTo(todayPoint.getRevenue()));
        assertEquals(1L, todayPoint.getBookingCount());
        assertEquals(0, new BigDecimal("108000.00").compareTo(todayPoint.getOnlineTransferRevenue()));
        assertEquals(1L, todayPoint.getOnlineTransferCount());
    }

    private WalletLedgerEntry settledEntry(
            Long id, Payment payment, String gross, String fee, String net, LocalDateTime createdAt) {
        return WalletLedgerEntry.builder()
                .id(id)
                .merchantId(merchantId)
                .bookingId(payment.getBookingId())
                .payment(payment)
                .entryType(WalletLedgerEntryTypeEnum.SETTLEMENT_CREDIT)
                .grossAmount(new BigDecimal(gross))
                .feePercent(new BigDecimal("10.0"))
                .feeAmount(new BigDecimal(fee))
                .netAmount(new BigDecimal(net))
                .createdAt(createdAt)
                .build();
    }

    private CashCheckoutReceiptRepository.LotCashAggregation cashAgg(Long lotId, BigDecimal total, Long count) {
        return new CashCheckoutReceiptRepository.LotCashAggregation() {
            @Override public Long getParkingLotId() { return lotId; }
            @Override public BigDecimal getTotalAmount() { return total; }
            @Override public Long getBookingCount() { return count; }
        };
    }
}
