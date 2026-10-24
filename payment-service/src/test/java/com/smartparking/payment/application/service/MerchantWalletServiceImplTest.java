package com.smartparking.payment.application.service;

import com.smartparking.common.exception.AppException;
import com.smartparking.common.exception.ErrorCode;
import com.smartparking.payment.application.dto.SettlementSplit;
import com.smartparking.payment.application.service.impl.MerchantWalletServiceImpl;
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
}
