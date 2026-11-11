package com.smartparking.payment.application.service;

import com.smartparking.common.enums.PaymentStatusEnum;
import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.impl.MerchantWalletServiceImpl;
import com.smartparking.payment.domain.model.Payment;
import com.smartparking.payment.domain.model.Wallet;
import com.smartparking.payment.domain.repository.PaymentRepository;
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

    @InjectMocks
    private MerchantWalletServiceImpl merchantWalletService;

    private final Long merchantId = 100L;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(merchantWalletService, "platformFeePercent", new BigDecimal("10.0"));
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
    @DisplayName("Get revenue summary calculates and returns correct totals and continuous 7 days series")
    void testGetRevenueSummary() {
        when(paymentRepository.sumTotalRevenueByMerchantId(merchantId)).thenReturn(new BigDecimal("200000"));
        when(ledgerRepository.sumPlatformFeeByMerchantId(merchantId)).thenReturn(new BigDecimal("20000"));
        when(ledgerRepository.sumNetAmountByMerchantId(merchantId)).thenReturn(new BigDecimal("180000"));
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
    @DisplayName("Get revenue summary aggregates daily revenue and booking counts from payments")
    void testGetRevenueSummaryWithPayments() {
        LocalDateTime todayNoon = LocalDate.now().atTime(12, 0);
        LocalDateTime yesterdayNoon = LocalDate.now().minusDays(1).atTime(14, 0);

        Payment p1 = Payment.builder()
                .id(1L)
                .merchantId(merchantId)
                .parkingLotId(10L)
                .bookingId(101L)
                .amount(new BigDecimal("100000"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        p1.setCreatedAt(todayNoon);

        Payment p2 = Payment.builder()
                .id(2L)
                .merchantId(merchantId)
                .parkingLotId(10L)
                .bookingId(102L)
                .amount(new BigDecimal("50000"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        p2.setCreatedAt(todayNoon);

        Payment p3 = Payment.builder()
                .id(3L)
                .merchantId(merchantId)
                .parkingLotId(10L)
                .bookingId(103L)
                .amount(new BigDecimal("200000"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        p3.setCreatedAt(yesterdayNoon);

        when(paymentRepository.findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(merchantId), eq(PaymentStatusEnum.SUCCESS), any(), any()))
                .thenReturn(List.of(p3, p1, p2));

        var summary = merchantWalletService.getRevenueSummary(merchantId);

        assertNotNull(summary);
        assertEquals(7, summary.getSeries().size());
        assertEquals(3L, summary.getCompletedBookings());

        String todayKey = LocalDate.now().toString();
        String yesterdayKey = LocalDate.now().minusDays(1).toString();

        var todayPoint = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(todayKey))
                .findFirst()
                .orElse(null);
        assertNotNull(todayPoint);
        // Fee 10%, Gross 150,000 -> Net 135,000
        assertEquals(new BigDecimal("135000.00"), todayPoint.getRevenue());
        assertEquals(2L, todayPoint.getBookingCount());

        var yesterdayPoint = summary.getSeries().stream()
                .filter(pt -> pt.getPeriod().equals(yesterdayKey))
                .findFirst()
                .orElse(null);
        assertNotNull(yesterdayPoint);
        // Fee 10%, Gross 200,000 -> Net 180,000
        assertEquals(new BigDecimal("180000.00"), yesterdayPoint.getRevenue());
        assertEquals(1L, yesterdayPoint.getBookingCount());
    }

    @Test
    @DisplayName("Get revenue summary filters by parking lot id when provided")
    void testGetRevenueSummaryWithParkingLotFilter() {
        Long parkingLotId = 99L;
        Payment p = Payment.builder()
                .id(1L)
                .merchantId(merchantId)
                .parkingLotId(parkingLotId)
                .bookingId(201L)
                .amount(new BigDecimal("80000"))
                .status(PaymentStatusEnum.SUCCESS)
                .build();
        p.setCreatedAt(LocalDateTime.now());

        when(paymentRepository.sumTotalRevenueByMerchantIdAndParkingLotId(eq(merchantId), eq(parkingLotId)))
                .thenReturn(new BigDecimal("80000"));
        when(paymentRepository.findByMerchantIdAndParkingLotIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(merchantId), eq(parkingLotId), eq(PaymentStatusEnum.SUCCESS), any(), any()))
                .thenReturn(List.of(p));

        var summary = merchantWalletService.getRevenueSummary(merchantId, parkingLotId, null, null, "DAY");

        assertNotNull(summary);
        assertEquals(1L, summary.getCompletedBookings());
        verify(paymentRepository, times(1)).findByMerchantIdAndParkingLotIdAndStatusAndCreatedAtBetweenOrderByCreatedAtAsc(
                eq(merchantId), eq(parkingLotId), eq(PaymentStatusEnum.SUCCESS), any(), any());
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
}
